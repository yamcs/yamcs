package org.yamcs.xtceproc;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.InvalidIdentification;
import org.yamcs.TmProcessor;
import org.yamcs.YProcessor;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.container.ContainerProvider;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

import com.google.common.util.concurrent.AbstractService;

/**
 * 
 * Does the job of getting containers and transforming them into parameters which are then sent to the 
 *  parameter request manager for the distribution to the requesters.
 * 
 * Relies on {@link XtceTmExtractor} for extracting the parameters out of containers
 * 
 *  @author mache
 * 
 */

public class XtceTmProcessor extends AbstractService implements TmProcessor, ParameterProvider, ContainerProvider {

    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    private ParameterRequestManager parameterRequestManager;
    private ContainerListener containerRequestManager;

    public final YProcessor processor;
    public final XtceDb xtcedb;
    final XtceTmExtractor tmExtractor;
    final String CONFIG_KEY_ignoreOutOfContainerEntries = "ignoreOutOfContainerEntries";
    
    public XtceTmProcessor(YProcessor proc, Map<String, Object> tmProcessorConfig) {
	log=LoggerFactory.getLogger(this.getClass().getName()+"["+proc.getName()+"]");
	this.processor=proc;
	this.xtcedb=proc.getXtceDb();
	tmExtractor=new XtceTmExtractor(xtcedb);
	if(tmProcessorConfig != null) {
	    if(tmProcessorConfig.containsKey(CONFIG_KEY_ignoreOutOfContainerEntries)) {
	        Object o = tmProcessorConfig.get(CONFIG_KEY_ignoreOutOfContainerEntries);
	        if(!(o instanceof Boolean)) {
	            throw new ConfigurationException(CONFIG_KEY_ignoreOutOfContainerEntries+" has to be a boolean");
	        }
	        boolean iooce = (Boolean) o;
	        tmExtractor.setIgnoreOutOfContainerEntries(iooce);
	    }
	}
    } 
    public XtceTmProcessor(YProcessor proc) {
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+proc.getName()+"]");
        this.processor=proc;
        this.xtcedb=proc.getXtceDb();
        tmExtractor=new XtceTmExtractor(xtcedb);
    }



    /**
     * Creates a TmProcessor to be used in "standalone" mode, outside of any channel.
     */
    public XtceTmProcessor(XtceDb xtcedb) {
        log=LoggerFactory.getLogger(this.getClass().getName());
        this.processor=null;
        this.xtcedb=xtcedb;
        tmExtractor=new XtceTmExtractor(xtcedb);
    }

    /**
     * See {@link XtceTmExtractor#setIgnoreOutOfContainerEntries}
     * @param ignoreOutOfContainerEntries
     */
    public void setIgnoreOutOfContainerEntries(boolean ignoreOutOfContainerEntries) {
        tmExtractor.setIgnoreOutOfContainerEntries(ignoreOutOfContainerEntries);
    }
    
    @Override
    public void init(YProcessor channel) throws ConfigurationException {

    }

    @Override
    public void setParameterListener(ParameterRequestManager p) {
        this.parameterRequestManager=p;
    }

    @Override
    public void setContainerListener(ContainerListener c) {
        this.containerRequestManager=c;
    }

    /**
     * Adds a parameter to the current subscription list: 
     *  finds all the SequenceContainers in which this parameter may appear and adds them to the list.
     *  also for each sequence container adds the parameter needed to instantiate the sequence container.
     * @param param parameter to be added to the current subscription list 
     */
    @Override
    public void startProviding(Parameter param) { 
        tmExtractor.startProviding(param);
    }

    /**
     * adds all parameters to the subscription
     */
    @Override
    public void startProvidingAll() {
        tmExtractor.startProvidingAll();
    }

    @Override
    public void stopProviding(Parameter param) {
        tmExtractor.stopProviding(param);
    }

    @Override
    public boolean canProvide(NamedObjectId paraId) {
        Parameter p = xtcedb.getParameter(paraId);
        if(p==null) return false;
        
        return xtcedb.getParameterEntries(p)!=null;
    }
    
    @Override
    public boolean canProvide(Parameter para) {
        return xtcedb.getParameterEntries(para)!=null;
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        Parameter p = xtcedb.getParameter(paraId);
        if(p==null) throw new InvalidIdentification(paraId);
        return p;
    }
    
    private long getCurrentTime() {
        if(processor!=null) {
            return processor.getCurrentTime();
        } else {
            return TimeEncoding.getWallclockTime();
        }
    }
    /**
     * Process telemetry packets
     *
     */
    @Override
    public void processPacket(PacketWithTime pwrt){
        try {
            ByteBuffer bb= ByteBuffer.wrap(pwrt.getPacket());
            tmExtractor.processPacket(bb, pwrt.getGenerationTime(), getCurrentTime());
        
            ParameterValueList paramResult=tmExtractor.getParameterResult();
            ArrayList<ContainerExtractionResult> containerResult=tmExtractor.getContainerResult();
            
            if((parameterRequestManager!=null) &&( paramResult.size()>0)) {
                //careful out of the synchronized block in order to avoid dead locks 
                //  with the parameterRequestManager trying to add/remove parameters 
                //  while we are sending updates
                parameterRequestManager.update(paramResult);
            }
        
            if((containerRequestManager!=null) && (containerResult.size()>0)) {
                containerRequestManager.update(containerResult);
            }
        
        } catch (Exception e) {
            log.error("got exception in tmprocessor ", e);
        }
    }
    
    @Override
    public void processPacket(PacketWithTime pwrt, SequenceContainer sc){
        try {
            ByteBuffer bb= ByteBuffer.wrap(pwrt.getPacket());
            tmExtractor.processPacket(bb, pwrt.getGenerationTime(), TimeEncoding.getWallclockTime(), sc);

            ParameterValueList paramResult=tmExtractor.getParameterResult();
            ArrayList<ContainerExtractionResult> containerResult=tmExtractor.getContainerResult();
            
            if((parameterRequestManager!=null) &&( paramResult.size()>0)) {
                //careful out of the synchronized block in order to avoid dead locks 
                //  with the parameterRequestManager trying to add/remove parameters 
                //  while we are sending updates
                parameterRequestManager.update(paramResult);
            }

            if((containerRequestManager!=null) && (containerResult.size()>0)) {
                containerRequestManager.update(containerResult);
            }

        } catch (Exception e) {
            log.error("got exception in tmprocessor ", e);
        }
    }

    @Override
    public void finished() {
        notifyStopped();
        if(processor!=null) processor.quit();
    }

    public void resetStatistics() {
        tmExtractor.resetStatistics();
    }

    public ProcessingStatistics getStatistics(){
        return tmExtractor.getStatistics();
    }

    @Override
    public boolean canProvideContainer(NamedObjectId containerId) {
        return xtcedb.getSequenceContainer(containerId) != null;
    }

    @Override
    public void startProviding(SequenceContainer container) {
        tmExtractor.startProviding(container);
    }

    @Override
    public void stopProviding(SequenceContainer container) {
        tmExtractor.stopProviding(container);
    }

    @Override
    public void startProvidingAllContainers() {
        tmExtractor.startProvidingAll();
    }

    @Override
    public Container getContainer(NamedObjectId containerId) throws InvalidIdentification {
        SequenceContainer c = xtcedb.getSequenceContainer(containerId);
        if(c==null) throw new InvalidIdentification(containerId);
        return c;
    }

    /*
    public void subscribePackets(List<ItemIdPacketConsumerStruct> iipcs) {
        synchronized(subscription) {
            for(ItemIdPacketConsumerStruct i:iipcs) {
                subscription.addSequenceContainer(i.def);
            }
        }
    }
     */

    public Subscription getSubscription() {
        return tmExtractor.getSubscription();
    }



    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }



    public XtceDb getXtceDb() {
        return xtcedb;
    }
}
