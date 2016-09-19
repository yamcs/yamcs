package org.yamcs.tctm;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.TmProcessor;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorException;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.archive.ReplayListener;
import org.yamcs.archive.ReplayServer;
import org.yamcs.archive.YarchReplay;
import org.yamcs.cmdhistory.CommandHistoryProvider;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.SystemToken;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SystemParameterDb;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.Subscription;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;

import com.google.common.util.concurrent.AbstractService;
import com.google.protobuf.MessageLite;


/**
 * Provides telemetry packets and processed parameters from the yamcs archive.
 * 
 * @author nm
 * 
 */
public class ReplayService extends AbstractService implements ReplayListener, ArchiveTmPacketProvider, ParameterProvider, CommandHistoryProvider {
    static final long timeout=10000;

    boolean loop;
    long start, stop; // start and stop times of playback request
    String[] packets; // array of opsnames of packets to subscribe
    EndAction endAction;
    static Logger log=LoggerFactory.getLogger(ReplayService.class.getName());

    ReplayRequest originalReplayRequest;
    private HashSet<Parameter> subscribedParameters=new HashSet<Parameter>();
    private ParameterRequestManagerImpl parameterRequestManager;
    TmProcessor tmProcessor;
    volatile long dataCount=0;
    final XtceDb xtceDb;
    final SystemParameterDb sysParamDb;
    volatile long replayTime;

    private final String yamcsInstance;
    YarchReplay yarchReplay;
    YProcessor yprocessor;
    //the originalReplayRequest contains possibly only parameters.
    //the modified one sent to the ReplayServer contains the raw data required for extracting/processing those parameters
    ReplayRequest.Builder rawDataRequest;
    CommandHistoryRequestManager commandHistoryRequestManager;

    
    public ReplayService(String instance, ReplayRequest spec) throws ProcessorException, ConfigurationException {
        this.yamcsInstance = instance;
        this.originalReplayRequest = spec;
        xtceDb = XtceDbFactory.getInstance(instance);
        sysParamDb = xtceDb.getSystemParameterDb();
    }


    @Override
    public void init(YProcessor proc) throws ConfigurationException {
        this.yprocessor = proc;
    }

    @Override
    public void init(YProcessor proc, TmProcessor tmProcessor) {
        this.tmProcessor = tmProcessor;
        this.yprocessor = proc;
    }

    @Override
    public boolean isArchiveReplay() {
        return true;
    }

    @Override
    public void newData(ProtoDataType type, MessageLite data) {
        switch(type) {
        case TM_PACKET:
            dataCount++;
            TmPacketData tpd = (TmPacketData)data;
            replayTime = tpd.getGenerationTime();
            tmProcessor.processPacket(new PacketWithTime(tpd.getReceptionTime(), tpd.getGenerationTime(), tpd.getPacket().toByteArray()));
            break;
        case PP:
            //convert from protobuf ParameterValue to internal ParameterValue 
            ParameterData pd = (ParameterData)data;
            ArrayList<ParameterValue> params=new ArrayList<ParameterValue>(pd.getParameterCount());
            for(org.yamcs.protobuf.Pvalue.ParameterValue pbPv:pd.getParameterList()) {
                
                Parameter ppDef;
                if(SystemParameterDb.isSystemParameter(pbPv.getId())) {
                    ppDef = sysParamDb.getSystemParameter(pbPv.getId(), true);
                } else {
                    ppDef = xtceDb.getParameter(pbPv.getId());
                }
                
                if(ppDef!=null) {
                    ParameterValue pv = ParameterValue.fromGpb(ppDef, pbPv);
                    params.add(pv);
                    replayTime = pv.getGenerationTime();
                } else { //this may happen if the xtcedb has been changed from when the archive has been built
                    log.trace("Received value for a parameter id not in xtceDb: {}", pbPv.getId());
                }
            }
            parameterRequestManager.update(params);
            break;
        case CMD_HISTORY:
            CommandHistoryEntry che = (CommandHistoryEntry) data;
            commandHistoryRequestManager.addCommand(PreparedCommand.fromCommandHistoryEntry(che));
            break;
        default:
            log.error("Unexpected data type {} received");            
        }

    }

    @Override
    public void stateChanged(ReplayStatus rs) {
        if(rs.getState()==ReplayState.CLOSED) {
            log.debug("End signal received");
            notifyStopped();
            tmProcessor.finished();
        } else {
            yprocessor.notifyStateChange();
        }
    }


    @Override
    public void doStop() {
        if(yarchReplay!=null) {
            yarchReplay.quit();
        }
        notifyStopped();
    }

    //finds out all raw data (TM and PP) required to provide the needed parameters.
    // in order to do this, subscribe to all parameters from the list, then check in the tmProcessor subscription which containers are needed
    // and in the subscribedParameters which PPs may be required
    private void createRawSubscription() throws YamcsException {
        rawDataRequest = originalReplayRequest.toBuilder().clearParameterRequest();
                
        List<NamedObjectId> plist = originalReplayRequest.getParameterRequest().getNameFilterList();
        if(plist.isEmpty()) return; 

        ParameterWithIdRequestHelper pidrm = new ParameterWithIdRequestHelper(parameterRequestManager, new ParameterWithIdConsumer() {
            @Override
            public void update(int subscriptionId, List<ParameterValueWithId> params) {//ignore data, we create this subscription just to get the list of dependent containers and PPs
            }
        });
        int subscriptionId;
        try {
            subscriptionId = pidrm.addRequest(plist, new SystemToken());
        } catch (InvalidIdentification e) {
            NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
            throw new YamcsException("InvalidIdentification", "Invalid identification", nol);
        } catch (NoPermissionException e) {
            throw new RuntimeException("Unexpected No permission");
        }

        XtceTmProcessor tmproc = yprocessor.getTmProcessor();
        Subscription subscription = tmproc.getSubscription();
        Collection<SequenceContainer> containers = subscription.getContainers();

        if((containers==null)|| (containers.isEmpty())) {
            log.debug("No container required for the parameter subscription");
        } else {
            PacketReplayRequest.Builder rawPacketRequest = originalReplayRequest.getPacketRequest().toBuilder();
            
            for(SequenceContainer sc: containers) {
                rawPacketRequest.addNameFilter(NamedObjectId.newBuilder().setName(sc.getQualifiedName()).build());
            }
            log.debug("after TM subscription, the request contains the following packets: "+rawPacketRequest);
            rawDataRequest.setPacketRequest(rawPacketRequest);
        }
        
        pidrm.removeRequest(subscriptionId);
        
        //now check for PPs
        
        Set<String> pprecordings = new HashSet<>();
        
        for(Parameter p: subscribedParameters) {
            pprecordings.add(p.getRecordingGroup());
        }
        if(pprecordings.isEmpty()) {
            log.debug("No aadditional pp group added to the subscription");
        } else {
            PpReplayRequest.Builder pprr = originalReplayRequest.getPpRequest().toBuilder();
            pprr.addAllGroupNameFilter(pprecordings);
            rawDataRequest.setPpRequest(pprr.build());
        }
        if(!rawDataRequest.hasPacketRequest() && !rawDataRequest.hasPpRequest()) {
            if(originalReplayRequest.hasParameterRequest()) {
                throw new YamcsException("Cannot find a replay source for any parmeters from request: "+originalReplayRequest.getParameterRequest().toString());
            } else {
                throw new YamcsException("Refusing to create an empty replay request");
            }
        }
    }

    private void createReplay() throws ProcessorException {
        ReplayServer replayServer = YamcsServer.getService(yamcsInstance, ReplayServer.class);
        if(replayServer==null) {
            throw new ProcessorException("ReplayServer not configured for this instance");
        }
        try {
            yarchReplay = replayServer.createReplay(rawDataRequest.build(), this, new SystemToken());
        } catch (YamcsException e) {
            log.error("Exception creating the replay", e);
            throw new ProcessorException("Exception creating the replay: "+e.getMessage(), e);
        }
    }
    @Override
    public void doStart() {
        try {
            createRawSubscription();
            createReplay();
        } catch (YamcsException | ProcessorException e){
            notifyFailed(e);
            return;
        }
        
        yarchReplay.start();
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
    public void seek(long time) {
        try {
            yarchReplay.seek(time);
        } catch (YamcsException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void setParameterListener(ParameterRequestManager parameterRequestManager) {
        this.parameterRequestManager = (ParameterRequestManagerImpl)parameterRequestManager;
    }

    @Override
    public void startProviding(Parameter paramDef) {
        
        synchronized(subscribedParameters) {
            subscribedParameters.add(paramDef);
        }
    }

    @Override
    public void startProvidingAll() {
        //TODO
    }


    @Override
    public void stopProviding(Parameter paramDef) {
        synchronized(subscribedParameters) {
            subscribedParameters.remove(paramDef);
        }
    }

    @Override
    public boolean canProvide(NamedObjectId id) {
        boolean result = false;
        Parameter p = xtceDb.getParameter(id); 
        if(p!=null) {
            result= canProvide(p);
        } else { //check if it's system parameter
            if(SystemParameterDb.isSystemParameter(id)) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean canProvide(Parameter p) {
        boolean result;
        if(xtceDb.getParameterEntries(p)!=null) {
            result = false;
        } else {
            result = true;
        }
        return result;
    }

    @Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
        Parameter p = xtceDb.getParameter(id);
        if(p==null) {
            if(SystemParameterDb.isSystemParameter(id)) {
                p = xtceDb.getSystemParameterDb().getSystemParameter(id, true);
            }

        }
        if(p==null) {
            throw new InvalidIdentification();
        } else {
            return p;
        }
    }

    @Override
    public ReplaySpeed getSpeed() {
        return originalReplayRequest.getSpeed();
    }

    @Override
    public ReplayRequest getReplayRequest() {
        return originalReplayRequest;
    }

    @Override
    public ReplayState getReplayState() {
        if(state() == State.NEW) {
            return ReplayState.INITIALIZATION;
        } else if(state() == State.FAILED) {
            return ReplayState.ERROR;
        } else {
            return yarchReplay.getState();
        }
    }

    @Override
    public long getReplayTime() {
        return replayTime;
    }


    @Override
    public void changeSpeed(ReplaySpeed speed) {
        yarchReplay.changeSpeed(speed);
        // need to change the replay request to get the proper value when getReplayRequest() is called
        originalReplayRequest = originalReplayRequest.toBuilder().setSpeed(speed).build();
    }

    @Override
    public void setCommandHistoryRequestManager(CommandHistoryRequestManager chrm) {
        this.commandHistoryRequestManager = chrm;
    }

}