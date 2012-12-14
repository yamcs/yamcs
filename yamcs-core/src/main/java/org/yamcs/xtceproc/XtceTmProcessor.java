package org.yamcs.xtceproc;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterListener;
import org.yamcs.ParameterProvider;
import org.yamcs.ParameterValue;
import org.yamcs.TmProcessor;
import org.yamcs.archive.PacketWithTime;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.StringConvertors;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

/**
 * 
 * Does the job of getting packets and transforming them into parameters which are then sent to the 
 *  parameter request manager for the distribution to the requesters.
 * 
 * Relies on {@link XtceTmExtractor} for extracting the parameters out of packets
 * 
 *  @author mache
 * 
 */

public class XtceTmProcessor extends AbstractService implements TmProcessor, ParameterProvider {
	Logger log=LoggerFactory.getLogger(this.getClass().getName());
	private ParameterListener parameterRequestManager;
	private ContainerListener containerListener;
	
	
	private final Channel channel;
	public final XtceDb xtcedb;
	final XtceTmExtractor tmExtractor;
	
	public XtceTmProcessor(Channel chan) {
	    log=LoggerFactory.getLogger(this.getClass().getName()+"["+chan.getName()+"]");
	    this.channel=chan;
	    this.xtcedb=chan.xtcedb;
	    tmExtractor=new XtceTmExtractor(xtcedb);
	}
	
	/**
	 * Creates a TmProcessor to be used in "standalone" mode, outside of any channel.
	 * @param p
	 * @param xtcedb
	 */
	public XtceTmProcessor(XtceDb xtcedb) {
		log=LoggerFactory.getLogger(this.getClass().getName());
		this.channel=null;
		this.xtcedb=xtcedb;
		tmExtractor=new XtceTmExtractor(xtcedb);
	}

	@Override
	public void setParameterListener(ParameterListener p) {
	    this.parameterRequestManager=p;
	}

	public void setContainerListener(ContainerListener c) {
	    this.containerListener=c;
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
		if(paraId.hasNamespace()) {
			return (xtcedb.getParameter(paraId.getNamespace(), paraId.getName())!=null);
		} else { 
			return (xtcedb.getParameter(paraId.getName())!=null);
		}
	}
	
	@Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
		Parameter p=paraId.hasNamespace()?xtcedb.getParameter(paraId.getNamespace(), paraId.getName()):xtcedb.getParameter(paraId.getName());
		if(p==null) throw new InvalidIdentification(paraId);
		return p;
	}
	
	/**
	 * Start processing telemetry packets
	 *
	 */
	@Override
    public void processPacket(PacketWithTime pwrt){

	    try {
	        ByteBuffer bb=pwrt.bb;

	        //we only support packets that have ccsds secondary header
	        if(bb.capacity()<16) {
	            log.warn("packet smaller than 16 bytes has been received size="+(bb.capacity())+" content:"+StringConvertors.byteBufferToHexString(bb));
	            return;
	        }
	        tmExtractor.processPacket(bb, pwrt.getGenerationTime());
	        
	        ArrayList<ParameterValue> paramResult=tmExtractor.getParameterResult();
	        ArrayList<SequenceContainer> containerResult=tmExtractor.getContainerResult();
	        
	        if((parameterRequestManager!=null) &&( paramResult.size()>0)) {
	            //careful out of the synchronized block in order to avoid dead locks 
	            //  with the parameterRequestManager trying to add/remove parameters 
	            //  while we are sending updates
	            parameterRequestManager.update(paramResult);
	        }
	        
	        if((containerListener!=null) && (containerResult.size()>0)) {
	            containerListener.update(containerResult);
	        }
	        
	        
	    } catch (Exception e) {
	        log.error("got exception in tmprocessor "+e);
	        e.printStackTrace();
	    }
	}
	
	@Override
    public void finished() {
	    notifyStopped();
        if(channel!=null) channel.quit();
	}
	
	public void resetStatistics() {
	    tmExtractor.resetStatistics();
	}
		
	public ProcessingStatistics getStatistics(){
		return tmExtractor.getStatistics();
	}


    public void startProviding(SequenceContainer sequenceContainer) {
        tmExtractor.startProviding(sequenceContainer);
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

    @Override
    public String getDetailedStatus() {
        // TODO Auto-generated method stub
        return null;
    }

}
