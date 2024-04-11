package org.yamcs.tctm;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.AbstractProcessorService;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.TmPacket;
import org.yamcs.TmProcessor;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.archive.ReplayListener;
import org.yamcs.archive.ReplayOptions;
import org.yamcs.archive.ReplayServer;
import org.yamcs.archive.SpeedSpec;
import org.yamcs.archive.XtceTmReplayHandler.ReplayPacket;
import org.yamcs.archive.YarchReplay;
import org.yamcs.cmdhistory.CommandHistoryProvider;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.ParameterTypeProcessor;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.Subscription;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.XtceTmProcessor;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.EventReplayRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.security.SecurityStore;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;

import com.google.protobuf.util.JsonFormat;

/**
 * Provides telemetry packets and processed parameters from the yamcs archive.
 * 
 */
public class ReplayService extends AbstractProcessorService
        implements ReplayListener, ArchiveTmPacketProvider, ParameterProvider, CommandHistoryProvider {
    static final long TIMEOUT = 10000;

    EndAction endAction;

    ReplayOptions originalReplayRequest;
    private HashSet<Parameter> subscribedParameters = new HashSet<>();
    private ParameterProcessorManager parameterProcessorManager;
    TmProcessor tmProcessor;
    Mdb mdb;

    YarchReplay yarchReplay;
    // the originalReplayRequest contains possibly only parameters.
    // the modified one sent to the ReplayServer contains the raw data required for extracting/processing those
    // parameters
    ReplayOptions rawDataRequest;
    CommandHistoryRequestManager commandHistoryRequestManager;

    private SecurityStore securityStore;

    // this can be set in the config (in processor.yaml) to exclude certain parameter groups from replay
    List<String> excludeParameterGroups = null;

    @Override
    public void init(Processor proc, YConfiguration args, Object spec) {
        super.init(proc, args, spec);
        mdb = MdbFactory.getInstance(getYamcsInstance());
        securityStore = YamcsServer.getServer().getSecurityStore();
        if (args.containsKey("excludeParameterGroups")) {
            excludeParameterGroups = args.getList("excludeParameterGroups");
        }
        this.tmProcessor = proc.getTmProcessor();
        parameterProcessorManager = proc.getParameterProcessorManager();
        proc.setPacketProvider(this);
        parameterProcessorManager.addParameterProvider(this);

        if (spec instanceof ReplayOptions) {
            originalReplayRequest = (ReplayOptions) spec;
        } else if (spec instanceof String) {
            ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
            try {
                JsonFormat.parser().merge((String) spec, rrb);
            } catch (IOException e) {
                throw new ConfigurationException("Cannot parse config into a replay request: " + e.getMessage(), e);
            }
            if (!rrb.hasSpeed()) {
                rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.REALTIME).setParam(1));
            }
            originalReplayRequest = new ReplayOptions(rrb.build());
        } else if (spec == null) { // For example, created by ProcessorCreatorService
            originalReplayRequest = new ReplayOptions();
            originalReplayRequest.setSpeed(new SpeedSpec(SpeedSpec.Type.ORIGINAL, 1));
            originalReplayRequest.setEndAction(EndAction.STOP);
            originalReplayRequest.setAutostart(false);
        } else {
            throw new IllegalArgumentException("Unknown spec of type " + spec.getClass());
        }
    }

    @Override
    public boolean isArchiveReplay() {
        return true;
    }

    @Override
    public void newData(ProtoDataType type, Object data) {
        switch (type) {
        case TM_PACKET:
            ReplayPacket rp = (ReplayPacket) data;
            String qn = rp.getQualifiedName();
            SequenceContainer container = mdb.getSequenceContainer(qn);
            if (container == null) {
                log.warn("Unknown sequence container '" + qn + "' found when replaying", qn);
            } else {
                SequenceContainer parent;
                while ((parent = container.getBaseContainer()) != null) {
                    container = parent;
                }

                tmProcessor.processPacket(new TmPacket(rp.getReceptionTime(), rp.getGenerationTime(),
                        rp.getSequenceNumber(), rp.getPacket()), container);
            }
            break;
        case PP:
            @SuppressWarnings("unchecked")
            List<ParameterValue> pvals = (List<ParameterValue>) data;
            if (!pvals.isEmpty()) {
                ProcessingData processingData = ProcessingData.createForTmProcessing(processor.getLastValueCache());
                calibrate(pvals, processingData);
                parameterProcessorManager.process(processingData);
            }
            break;
        case CMD_HISTORY:
            CommandHistoryEntry che = (CommandHistoryEntry) data;
            commandHistoryRequestManager.addCommand(PreparedCommand.fromCommandHistoryEntry(che));
            break;
        case EVENT:
            Event evt = (Event) data;
            break;
        default:
            log.error("Unexpected data type {} received", type);
        }
    }

    private void calibrate(List<ParameterValue> pvlist, ProcessingData processingData) {
        ParameterTypeProcessor ptypeProcessor = processor.getProcessorData().getParameterTypeProcessor();

        for (ParameterValue pv : pvlist) {
            if (pv.getEngValue() == null && pv.getRawValue() != null) {
                ptypeProcessor.calibrate(processingData, pv);
            }
            processingData.addTmParam(pv);
        }
    }

    @Override
    public void stateChanged(ReplayStatus rs) {
        if (rs.getState() == ReplayState.CLOSED) {
            log.debug("End signal received");
            notifyStopped();
            tmProcessor.finished();
        } else {
            processor.notifyStateChange();
        }
    }

    @Override
    public void doStop() {
        if (yarchReplay != null) {
            yarchReplay.quit();
        }
        notifyStopped();
    }

    // Create rawDataRequest from originalReplayRequest by finding out all raw data (TM and PP) required to provide the
    // needed parameters. The raw request must not contain parameters but only TM or PP.
    //
    // in order to do this, the method addPacketsRequiredForParams will subscribe to all parameters part of the original
    // request, then check in the tmProcessor subscription which containers are needed and in the subscribedParameters
    // which PPs may be required
    private void createRawSubscription() throws YamcsException {

        boolean replayAll = originalReplayRequest.isReplayAll();

        Set<String> ppRecFilter = new HashSet<>();
        if (replayAll) {
            rawDataRequest = new ReplayOptions(originalReplayRequest);
            rawDataRequest.setPacketRequest(PacketReplayRequest.newBuilder().build());
            rawDataRequest.setEventRequest(EventReplayRequest.newBuilder().build());
            rawDataRequest.setPpRequest(PpReplayRequest.newBuilder().build());
            rawDataRequest.setCommandHistoryRequest(CommandHistoryReplayRequest.newBuilder().build());
        } else {
            rawDataRequest = new ReplayOptions(originalReplayRequest);
            rawDataRequest.clearParameterRequest();
            addPacketsRequiredForParams();

            // addPacketsRequiredForParams above has caused the parameter request manager to populate the
            // subscribedParameters set; in case we do not have to retrieve all parameters, create a pp filter such that
            // only the required pps are replayed
            if (!originalReplayRequest.isReplayAllParameters()) {
                for (Parameter p : subscribedParameters) {
                    ppRecFilter.add(p.getRecordingGroup());
                }
            }
        }

        if (ppRecFilter.isEmpty() && excludeParameterGroups == null) {
            log.debug("No additional pp group added or removed to/from the subscription");
        } else {
            PpReplayRequest ppreq = originalReplayRequest.getPpRequest();
            PpReplayRequest.Builder pprr = ppreq.toBuilder();
            pprr.addAllGroupNameFilter(ppRecFilter);
            if (excludeParameterGroups != null) {
                pprr.addAllGroupNameExclude(excludeParameterGroups);
            }
            rawDataRequest.setPpRequest(pprr.build());

        }
        if (!rawDataRequest.hasPacketRequest() && !rawDataRequest.hasPpRequest()) {
            if (originalReplayRequest.hasParameterRequest()) {
                throw new YamcsException("Cannot find a replay source for any parmeters from request: "
                        + originalReplayRequest.getParameterRequest().toString());
            } else {
                throw new YamcsException("Refusing to create an empty replay request");
            }
        }
    }

    private void addPacketsRequiredForParams() throws YamcsException {
        List<NamedObjectId> plist = originalReplayRequest.getParameterRequest().getNameFilterList();
        if (plist.isEmpty()) {
            return;
        }
        ParameterWithIdRequestHelper pidrm = new ParameterWithIdRequestHelper(
                parameterProcessorManager.getParameterRequestManager(),
                (subscriptionId, params) -> {
                    // ignore data, we create this subscription just to get the list of
                    // dependent containers and PPs
                });
        int subscriptionId;
        try {
            subscriptionId = pidrm.addRequest(plist, securityStore.getSystemUser());
        } catch (InvalidIdentification e) {
            NamedObjectList nol = NamedObjectList.newBuilder().addAllList(e.getInvalidParameters()).build();
            throw new YamcsException("InvalidIdentification", "Invalid identification", nol);
        } catch (NoPermissionException e) {
            throw new IllegalStateException("Unexpected No permission");
        }

        XtceTmProcessor tmproc = processor.getTmProcessor();
        Subscription subscription = tmproc.getSubscription();
        Collection<SequenceContainer> containers = subscription.getContainers();

        if ((containers == null) || (containers.isEmpty())) {
            log.debug("No container required for the parameter subscription");
        } else {
            PacketReplayRequest.Builder rawPacketRequest = originalReplayRequest.getPacketRequest().toBuilder();

            for (SequenceContainer sc : containers) {
                rawPacketRequest.addNameFilter(NamedObjectId.newBuilder().setName(sc.getQualifiedName()).build());
            }
            log.debug("after TM subscription, the request contains the following packets: "
                    + rawPacketRequest.getNameFilterList());
            rawDataRequest.setPacketRequest(rawPacketRequest.build());
        }
        pidrm.removeRequest(subscriptionId);
    }

    private void createReplay() throws ProcessorException {
        ReplayServer replayServer = YamcsServer.getServer().getService(getYamcsInstance(), ReplayServer.class);
        if (replayServer == null) {
            throw new ProcessorException("ReplayServer not configured for this instance");
        }
        try {
            yarchReplay = replayServer.createReplay(rawDataRequest, this);
        } catch (YamcsException e) {
            log.error("Exception creating the replay", e);
            throw new ProcessorException("Exception creating the replay: " + e.getMessage(), e);
        }
    }

    @Override
    public void doStart() {
        try {
            createRawSubscription();
            createReplay();
        } catch (YamcsException e) {
            notifyFailed(e);
            return;
        }

        if (originalReplayRequest.isAutostart()) {
            yarchReplay.start();
        }
        notifyStarted();
    }

    @Override
    public void pause() {
        yarchReplay.pause();
    }

    @Override
    public void resume() {
        yarchReplay.start();
    }

    @Override
    public void seek(long time, boolean autostart) {
        try {
            yarchReplay.seek(time, autostart);
        } catch (YamcsException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setParameterProcessor(ParameterProcessor ppm) {
        this.parameterProcessorManager = (ParameterProcessorManager) ppm;
    }

    @Override
    public void startProviding(Parameter paramDef) {
        // the subscribedParameters is used at the beginning to select the PP parameters which have to be subscribed
        synchronized (subscribedParameters) {
            subscribedParameters.add(paramDef);
        }
    }

    @Override
    public void startProvidingAll() {
        // ignore as we always provide all parameters
    }

    @Override
    public void stopProviding(Parameter paramDef) {
        synchronized (subscribedParameters) {
            subscribedParameters.remove(paramDef);
        }
    }

    @Override
    public boolean canProvide(NamedObjectId id) {
        boolean result = false;
        Parameter p = mdb.getParameter(id);
        if (p != null) {
            result = canProvide(p);
        } else { // check if it's system parameter
            if (Mdb.isSystemParameter(id)) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean canProvide(Parameter p) {
        boolean result;
        if (mdb.getParameterEntries(p) != null) {
            result = false;
        } else {
            result = true;
        }
        return result;
    }

    @Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
        Parameter p = mdb.getParameter(id);
        if (p == null) {
            throw new InvalidIdentification();
        } else {
            return p;
        }
    }

    @Override
    public ReplaySpeed getSpeed() {
        return originalReplayRequest.getSpeed().toProtobuf();
    }

    @Override
    public ReplayRequest getReplayRequest() {
        return originalReplayRequest.toProtobuf();
    }

    @Override
    public ReplayRequest getCurrentReplayRequest() {
        return yarchReplay != null ? yarchReplay.getCurrentReplayRequest().toProtobuf() : getReplayRequest();
    }

    @Override
    public ReplayState getReplayState() {
        if (state() == State.NEW) {
            return ReplayState.INITIALIZATION;
        } else if (state() == State.FAILED) {
            return ReplayState.ERROR;
        } else {
            return yarchReplay.getState();
        }
    }

    @Override
    public long getReplayTime() {
        if (yarchReplay != null) {
            return yarchReplay.getReplayTime();
        } else {
            return originalReplayRequest.getRangeStart();
        }
    }

    @Override
    public void changeSpeed(ReplaySpeed speed) {
        yarchReplay.changeSpeed(SpeedSpec.fromProtobuf(speed));
    }

    @Override
    public void changeEndAction(EndAction endAction) {
        yarchReplay.changeEndAction(endAction);
    }

    @Override
    public void changeRange(long start, long stop) {
        try {
            yarchReplay.changeRange(start, stop);
        } catch (YamcsException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setCommandHistoryRequestManager(CommandHistoryRequestManager chrm) {
        this.commandHistoryRequestManager = chrm;
    }
}
