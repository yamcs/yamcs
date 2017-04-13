package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingPosition;
import org.yamcs.xtceproc.ContainerProcessingContext.ContainerProcessingResult;

/**
 * 
 * Extracts parameters out of packets based on the XTCE description
 *
 * 
 */
public class XtceTmExtractor {
    private static final Logger log = LoggerFactory.getLogger(XtceTmExtractor.class);
    protected final Subscription subscription;
    private ProcessingStatistics stats=new ProcessingStatistics();

    private ContainerProcessingResult result;
    
    public final XtceDb xtcedb;
    final SequenceContainer rootContainer;
    boolean ignoreOutOfContainerEntries = false;
    final ProcessorData pcontext;
    /**
     * Creates a TmExtractor extracting data according to the XtceDb
     * @param xtcedb
     */
    public XtceTmExtractor(XtceDb xtcedb) {
        this(xtcedb, new ProcessorData());
    }
    /**
     * Create a new TM extractor with the given context
     * 
     * @param xtcedb
     * @param pcontext
     */
    public XtceTmExtractor(XtceDb xtcedb, ProcessorData pcontext) {
        this.xtcedb = xtcedb;
        this.subscription = new Subscription(xtcedb);
        rootContainer = xtcedb.getRootSequenceContainer();
        this.pcontext = pcontext;
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
    }

    /**
     * Extract one packet, starting at the root sequence container
     */
    public void processPacket(ByteBuffer bb, long generationTime, long acquisitionTime) {
        processPacket(bb, generationTime, acquisitionTime, rootContainer);
    }

    /**
     * Extract one packet, starting at the specified container.
     * @param bb 
     * @param generationTime 
     * @param aquisitionTime 
     * @param startContainer 
     */
    public void processPacket(ByteBuffer bb, long generationTime, long aquisitionTime, SequenceContainer startContainer) {
        result = new ContainerProcessingResult(aquisitionTime, generationTime, stats);
        try {
             synchronized(subscription) {
                ContainerProcessingPosition position = new ContainerProcessingPosition(bb, 0, 0);
                ContainerProcessingContext cpc = new ContainerProcessingContext(pcontext, position, result, subscription, ignoreOutOfContainerEntries);
                cpc.sequenceContainerProcessor.extract(startContainer);
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
    }
    

    public ParameterValueList getParameterResult() {
        return result.params;
    }

    public List<ContainerExtractionResult> getContainerResult() {
        return result.containers;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public String toString() {
        return subscription.toString();
    }
}
