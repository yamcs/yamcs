package org.yamcs.xtceproc;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.IndirectParameterRefEntry;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
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
    private ProcessingStatistics stats = new ProcessingStatistics();

    private ContainerProcessingResult result;

    public final XtceDb xtcedb;
    final SequenceContainer rootContainer;
    ContainerProcessingOptions options = new ContainerProcessingOptions();
    final ProcessorData pdata;

    /**
     * Creates a TmExtractor extracting data according to the XtceDb
     * 
     * @param xtcedb
     */
    public XtceTmExtractor(XtceDb xtcedb) {
        this(xtcedb, new ProcessorData(null, "XTCEPROC", xtcedb, false));
    }

    /**
     * Create a new TM extractor with the given context
     *
     * @param xtcedb
     * @param pdata
     */
    public XtceTmExtractor(XtceDb xtcedb, ProcessorData pdata) {
        this.xtcedb = xtcedb;
        this.subscription = new Subscription(xtcedb);
        rootContainer = xtcedb.getRootSequenceContainer();
        this.pdata = pdata;
    }

    /**
     * Adds a parameter to the current subscription list.
     * <ul>
     * <li>finds all the SequenceContainers in which this parameter may appear and adds them to the list.</li>
     * <li>for each sequence container adds the parameter needed to instantiate the sequence container.</li>
     * <li>if there is any {@link IndirectParameterRefEntry} having its alias namespace as one of the aliases of
     * <code>param</code>, adds it also to the subcription</li>
     * </ul>
     * 
     * @param param
     *            parameter to be added to the current subscription list
     */
    public void startProviding(Parameter param) {
        synchronized (subscription) {
            ParameterType ptype = param.getParameterType();
            if (ptype != null) {
                Set<Parameter> dependencies = ptype.getDependentParameters();
                dependencies.forEach(p -> subscription.addParameter(p));
            }
            subscription.addParameter(param);
        }
    }

    /**
     * Adds all containers and parameters to the subscription
     */
    public void provideAll() {
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
    public void processPacket(byte[] b, long generationTime, long acquisitionTime) {
        processPacket(new BitBuffer(b), generationTime, acquisitionTime, rootContainer);
    }

    /**
     * Extract one packet, starting at the root sequence container
     */
    public void processPacket(BitBuffer buf, long generationTime, long acquisitionTime) {
        processPacket(buf, generationTime, acquisitionTime, rootContainer);
    }

    /**
     * Extract one packet, starting at the specified container.
     */
    public void processPacket(byte[] b, long generationTime, long acquisitionTime, SequenceContainer startContainer) {
        processPacket(new BitBuffer(b), generationTime, acquisitionTime, startContainer);
    }

    /**
     * Extract one packet, starting at the specified container.
     */
    public void processPacket(BitBuffer buf, long generationTime, long acquisitionTime,
            SequenceContainer startContainer) {
        result = new ContainerProcessingResult(acquisitionTime, generationTime, stats);
        try {
            synchronized (subscription) {
                ContainerProcessingContext cpc = new ContainerProcessingContext(pdata, buf, result, subscription,
                        options);
                cpc.sequenceContainerProcessor.extract(startContainer);
            }
        } catch (XtceProcessingException e) {
            pdata.eventProducer.sendWarning(e.getMessage());
        } catch (Exception e) {
            log.error("got exception in tmextractor ", e);
        }
    }

    public void resetStatistics() {
        stats.reset();
    }

    public ProcessingStatistics getStatistics() {
        return stats;
    }

    public void startProviding(SequenceContainer sequenceContainer) {
        synchronized (subscription) {
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

    public void setOptions(ContainerProcessingOptions opts) {
        this.options = opts;
    }
}
