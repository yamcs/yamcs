package org.yamcs.xtceproc;


import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

/**
 * 
 * Extracts parameters out of packets based on the XTCE description
 *
 * 
 */
public class XtceTmExtractor {
    private static final Logger log=LoggerFactory.getLogger(XtceTmExtractor.class);
    protected final Subscription subscription;
    private ProcessingStatistics stats=new ProcessingStatistics();

    public final XtceDb xtcedb;
    final SequenceContainer rootContainer;
    ParameterValueList paramResult=new ParameterValueList();

    ArrayList<ContainerExtractionResult> containerResult=new ArrayList<ContainerExtractionResult>();
    boolean ignoreOutOfContainerEntries = false;
    
    /**
     * Creates a TmExtractor extracting data according to the XtceDb
     * @param xtcedb
     */
    public XtceTmExtractor(XtceDb xtcedb) {
        this.xtcedb=xtcedb;
        this.subscription=new Subscription(xtcedb);
        rootContainer=xtcedb.getRootSequenceContainer();
    }

    /**
     * Adds a parameter to the current subscription list: 
     *  finds all the SequenceContainers in which this parameter may appear and adds them to the list.
     *  also for each sequence container adds the parameter needed to instantiate the sequence container.
     * @param param parameter to be added to the current subscription list 
     */
    public void startProviding(Parameter param) {
        synchronized(subscription) {
            subscription.addParameter(param);
        }
    }

    /**
     * Adds all containers and parameters to the subscription
     */
    public void startProvidingAll() {
        for (SequenceContainer c : xtcedb.getSequenceContainers()) {
            if (c.getBaseContainer() == null) {
                subscription.addAll(c);
            }
        }
    }

    public void stopProviding(Parameter param) {
        //TODO 2.0 do something here
    }

    /**
     * Extract one packet, starting at the root sequence container
     */
    public void processPacket(ByteBuffer bb, long generationTime, long acquisitionTime) {
        processPacket(bb, generationTime, acquisitionTime, rootContainer);
    }

    /**
     * Extract one packet, starting at the specified container.
     */
    public void processPacket(ByteBuffer bb, long generationTime, long aquisitionTime, SequenceContainer startContainer) {
        try {
            paramResult = new ParameterValueList();
            containerResult = new ArrayList<ContainerExtractionResult>();
            synchronized(subscription) {
                ProcessingContext pcontext=new ProcessingContext(bb, 0, 0, subscription, paramResult, containerResult, aquisitionTime, generationTime, stats, ignoreOutOfContainerEntries);

                pcontext.sequenceContainerProcessor.extract(startContainer);
            }
        } catch (Exception e) {
            log.error("got exception in tmextractor ", e);
        }
    }

    public void resetStatistics() {
        stats.reset();
    }

    public ProcessingStatistics getStatistics(){
        return stats;
    }
    
    public boolean isIgnoreOutOfContainerEntries() {
        return ignoreOutOfContainerEntries;
    }

    /**
     * If set to true, the entries that  fit outside the packet definition, will not be even logged.
     * If set to false, a log message at WARNING level will be printed for the first entry that fits outside the binary packet.
     * 
     * In both cases, the processing stops at first such entry.
     *  
     * @param ignoreOutOfContainerEntries
     */
    public void setIgnoreOutOfContainerEntries(boolean ignoreOutOfContainerEntries) {
        this.ignoreOutOfContainerEntries = ignoreOutOfContainerEntries;
    }

    public void startProviding(SequenceContainer sequenceContainer) {
        synchronized(subscription) {
            subscription.addSequenceContainer(sequenceContainer);
        }
    }

    public void stopProviding(SequenceContainer sequenceContainer) {
        //TODO
    }
    

    public ParameterValueList getParameterResult() {
        return paramResult;
    }

    public ArrayList<ContainerExtractionResult> getContainerResult() {
        return containerResult;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public String toString() {
        return subscription.toString();
    }
}
