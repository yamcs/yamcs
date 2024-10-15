package org.yamcs.mdb;

import java.util.Collections;
import java.util.List;

import org.yamcs.AbstractProcessorService;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorConfig;
import org.yamcs.TmPacket;
import org.yamcs.TmProcessor;
import org.yamcs.YConfiguration;
import org.yamcs.container.ContainerProvider;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;

/**
 * 
 * Does the job of getting containers and transforming them into parameters which are then sent to the parameter request
 * manager for the distribution to the requesters.
 * 
 * Relies on {@link XtceTmExtractor} for extracting the parameters out of containers
 * 
 */

public class XtceTmProcessor extends AbstractProcessorService
        implements TmProcessor, ParameterProvider, ContainerProvider {
    private ParameterProcessor parameterProcessorManager;
    private ContainerListener containerRequestManager;

    public final Mdb mdb;
    final XtceTmExtractor tmExtractor;

    public XtceTmProcessor(Processor processor) {
        this.processor = processor;
        this.mdb = processor.getMdb();
        log = new Log(getClass(), processor.getInstance());
        log.setContext(processor.getName());
        tmExtractor = new XtceTmExtractor(mdb, processor.getProcessorData());
    }

    /**
     * Creates a TmProcessor to be used in "standalone" mode, outside of any processor It still uses the processor
     * config for configuration parameters relevant to container processing
     * 
     */
    public XtceTmProcessor(Mdb mdb, ProcessorConfig pconfig) {
        this.processor = null;
        this.mdb = mdb;
        log = new Log(getClass());

        String yamcsInstance = processor == null ? null : processor.getInstance();
        String procName = processor == null ? "XTCEPROC" : processor.getName();
        var pdata = new ProcessorData(yamcsInstance, procName, mdb, pconfig, Collections.emptyMap());
        tmExtractor = new XtceTmExtractor(mdb, pdata);
    }

    @Override
    public void init(Processor processor, YConfiguration config, Object spec) throws ConfigurationException {
        super.init(processor, config, spec);
    }

    @Override
    public void setParameterProcessor(ParameterProcessor p) {
        this.parameterProcessorManager = p;
    }

    @Override
    public void setContainerListener(ContainerListener c) {
        this.containerRequestManager = c;
    }

    /**
     * Adds a parameter to the current subscription list: finds all the SequenceContainers in which this parameter may
     * appear and adds them to the list. also for each sequence container adds the parameter needed to instantiate the
     * sequence container.
     * 
     * @param param
     *            parameter to be added to the current subscription list
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
        tmExtractor.provideAll();
    }

    @Override
    public void stopProviding(Parameter param) {
        tmExtractor.stopProviding(param);
    }

    @Override
    public boolean canProvide(NamedObjectId paraId) {
        Parameter p = mdb.getParameter(paraId);
        if (p == null) {
            return false;
        }

        return mdb.getParameterEntries(p) != null;
    }

    @Override
    public boolean canProvide(Parameter para) {
        return mdb.getParameterEntries(para) != null;
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        Parameter p = mdb.getParameter(paraId);
        if (p == null) {
            throw new InvalidIdentification(paraId);
        }
        return p;
    }

    /**
     * Process telemetry packets
     *
     */
    @Override
    public void processPacket(TmPacket pkt, SequenceContainer sc) {
        try {
            long rectime = pkt.getReceptionTime();
            if (rectime == TimeEncoding.INVALID_INSTANT) {
                rectime = TimeEncoding.getWallclockTime();
            }
            SequenceContainer rootContainer = pkt.getRootContainer();
            if (rootContainer == null) {
                rootContainer = sc;
            }
            ContainerProcessingResult result = tmExtractor.processPacket(pkt.getPacket(), pkt.getGenerationTime(),
                    rectime, pkt.getSeqCount(), rootContainer);

            ParameterValueList paramResult = result.getTmParams();
            List<ContainerExtractionResult> containerResult = result.containers;

            if ((containerRequestManager != null) && (containerResult.size() > 0)) {
                containerRequestManager.update(containerResult);
            }

            if ((parameterProcessorManager != null) && (paramResult.size() > 0)) {
                parameterProcessorManager.process(result);
            }
        } catch (Exception e) {
            log.error("Exception while processing packet", e);
        }
    }

    @Override
    public void finished() {
        stopAsync();
    }

    public void resetStatistics() {
        tmExtractor.resetStatistics();
    }

    public ProcessingStatistics getStatistics() {
        return tmExtractor.getStatistics();
    }

    @Override
    public boolean canProvideContainer(NamedObjectId containerId) {
        return mdb.getSequenceContainer(containerId) != null;
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
        tmExtractor.provideAll();
    }

    @Override
    public Container getContainer(NamedObjectId containerId) throws InvalidIdentification {
        SequenceContainer c = mdb.getSequenceContainer(containerId);
        if (c == null) {
            throw new InvalidIdentification(containerId);
        }
        return c;
    }

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

    public Mdb getMdb() {
        return mdb;
    }
}
