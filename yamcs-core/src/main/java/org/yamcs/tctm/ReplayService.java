package org.yamcs.tctm;

import static org.yamcs.api.Protocol.DATA_TYPE_HEADER_NAME;
import static org.yamcs.api.Protocol.decode;

import java.util.ArrayList;
import java.util.HashSet;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ChannelException;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterValue;
import org.yamcs.TmProcessor;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Instant;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;


/**
 * Provides telemetry packets and processed parameters from the yamcs archive received via hornetq.
 * 
 * TODO: there should be a way to receive this data directly without the need of hornetq - that will avoid translating PP parameters
 * 
 * @author nm
 * 
 */
public class ReplayService extends AbstractService implements MessageHandler, ArchiveTmPacketProvider, ParameterProvider {
    static final long timeout=10000;
    final SimpleString packetReplayAddress;

    boolean loop;
    long start, stop; // start and stop times of playback request
    String[] packets; // array of opsnames of packets to subscribe
    EndAction endAction;
    static Logger log=LoggerFactory.getLogger(ReplayService.class.getName());
    ReplayRequest replayRequest;
    private HashSet<Parameter> subscribedParameters=new HashSet<Parameter>();
    private ParameterRequestManager parameterRequestManager;
    final YamcsClient yclient;
    final YamcsSession ysession;
    TmProcessor tmProcessor;
    volatile long dataCount=0;
    XtceDb xtceDb;

    public ReplayService(String instance, String spec) throws ChannelException, ConfigurationException {
	xtceDb = XtceDbFactory.getInstance(instance);
	try {
	    String[] parts = spec.split(" ");
	    String archiveInstance=parts[0];
	    ysession=YamcsSession.newBuilder().build();
	    yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();

	    try {
		start = Long.parseLong(parts[1]);
		stop = Long.parseLong(parts[2]);
	    } catch (NumberFormatException e) {
		throw new ChannelException("could not parse:" +e);
	    }
	    try {
		if("STOP".equals(parts[3])) {
		    endAction=EndAction.QUIT;
		}  else {
		    endAction=EndAction.valueOf(parts[3]);
		}
	    } catch (IllegalArgumentException e) {
		throw new ChannelException(e.getMessage());
	    }

	    int cnt=4;
	    ReplaySpeed speed;

	    if(parts[cnt].equalsIgnoreCase("REALTIME")) {
		cnt++;
		speed=ReplaySpeed.newBuilder().setType(ReplaySpeedType.REALTIME).setParam(Float.parseFloat(parts[cnt++])).build();
	    } else if (parts[cnt].equalsIgnoreCase("AFAP")) {
		cnt++;
		speed=ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build();
	    } else if(parts[cnt].equalsIgnoreCase("FIXED_DELAY")) {
		cnt++;
		speed=ReplaySpeed.newBuilder().setType(ReplaySpeedType.FIXED_DELAY).setParam(Float.parseFloat(parts[cnt++])).build();
	    } else {
		throw new ChannelException("speed has to be one of REALTIME, AFAP or FIXED_DELAY");
	    }

	    if ( parts.length > cnt ) {
		packets = new String[parts.length - cnt];
		for ( int i = cnt; i < parts.length; ++i ) {
		    packets[i - cnt] = parts[i];
		}
	    } else {
		throw new ChannelException("no packets specified");
	    }

	    ReplayRequest.Builder rrbuilder=ReplayRequest.newBuilder().setStart(start).setStop(stop).
		    setEndAction(endAction).setSpeed(speed);

	    PacketReplayRequest.Builder packetRequestBuilder = PacketReplayRequest.newBuilder();
	    PpReplayRequest.Builder ppRequestBuilder = PpReplayRequest.newBuilder();
	    for(String packet:packets) {
		if(packet.startsWith("PP_")) {
		    ppRequestBuilder.addGroupNameFilter(packet);
		} else {
		    packetRequestBuilder.addNameFilter(NamedObjectId.newBuilder().setName(packet).setNamespace(MdbMappings.MDB_OPSNAME));
		}
	    }

	    if (!packetRequestBuilder.getNameFilterList().isEmpty())
		rrbuilder.setPacketRequest(packetRequestBuilder);
	    if (!ppRequestBuilder.getGroupNameFilterList().isEmpty())
		rrbuilder.setPpRequest(ppRequestBuilder);

	    replayRequest=rrbuilder.build();
	    StringMessage answer=(StringMessage) yclient.executeRpc(Protocol.getYarchReplayControlAddress(archiveInstance),
		    "createReplay", replayRequest, StringMessage.newBuilder());
	    packetReplayAddress=new SimpleString(answer.getMessage());
	    yclient.dataConsumer.setMessageHandler(this);

	} catch (HornetQException e) {
	    throw new ChannelException(e.toString());
	} catch (YamcsException e) {
	    throw new ChannelException(e.toString());
	} catch (YamcsApiException e) {
	    throw new ChannelException(e.getMessage(), e);
	}
    }

    @Override
    public void init(Channel channel) throws ConfigurationException {
    }

    @Override
    public void setTmProcessor(TmProcessor tmProcessor) {
	this.tmProcessor=tmProcessor;
    }

    @Override
    public boolean isArchiveReplay() {
	return true;
    }

    @Override
    public void onMessage(ClientMessage msg) {
	try {
	    if(msg==null) {
		log.warn("Null message received (maybe the yarch server has quit?)");
		tmProcessor.finished();
		return;
	    }
	    ProtoDataType dt=ProtoDataType.valueOf(msg.getIntProperty(DATA_TYPE_HEADER_NAME));
	    switch(dt) {
	    case TM_PACKET:
		dataCount++;
		TmPacketData tpd=(TmPacketData)decode(msg, TmPacketData.newBuilder());
		tmProcessor.processPacket(new PacketWithTime(tpd.getReceptionTime(), tpd.getGenerationTime(), tpd.getPacket().toByteArray()));
		break;
	    case PP:
		//convert from protobuf ParameterValue to internal ParameterValue 
		ParameterData pd=(ParameterData)decode(msg, ParameterData.newBuilder());
		ArrayList<ParameterValue> params=new ArrayList<ParameterValue>(pd.getParameterCount());
		for(org.yamcs.protobuf.Pvalue.ParameterValue pbPv:pd.getParameterList()) {
		    Parameter ppDef=xtceDb.getParameter(pbPv.getId());
		    ParameterValue pv=ParameterValue.fromGpb(ppDef, pbPv);
		    if(pv!=null) params.add(pv);
		}
		parameterRequestManager.update(params);
		break;
	    case STATE_CHANGE:
		ReplayStatus rs=(ReplayStatus)decode(msg, ReplayStatus.newBuilder());
		if(rs.getState()==ReplayState.CLOSED) {
		    log.debug("End signal received");
		    notifyStopped();
		    closeYSession();
		    tmProcessor.finished();
		}
		break;
	    }
	} catch (Exception e) {
	    log.warn("Error when receiving data : ", e);
	    e.printStackTrace();
	    tmProcessor.finished();
	}
    }
    /*
    private void processPps (List<PpData> list) {
        ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();

        for(PpData ppd:list) {
            ByteBuffer pp=ppd.getPp().asReadOnlyByteBuffer();
            long umi=PpUtils.getUmi(pp);
            ProcessedParameterDefinition ppDef=umi2PpMap.get(umi);
            if(ppDef!=null) {
                if (!subscribedParameters.contains(ppDef)) continue;
                ParameterValue pv=PpUtils.getParameterValueFromProcessedDataBuffer(ppDef,pp);
                if(pv!=null) params.add(pv);
            }
        }
        log.trace("pps subscribed {}", params);
        if(params.size()>0) {
            try {
                parameterRequestManager.update(params);
            } catch (Exception e) {
                log.error("Exception caught when propagatig the processed parameters: "+e);
            }
        }
    }
     */
    @Override
    public String getLinkStatus() {
	if(isRunning()) {
	    return "OK";
	} else {
	    return "UNAVAIL";
	}
    }

    public String getStatusInfo() {
	return getLinkStatus();
    }


    @Override
    public void doStop() {
	try {
	    yclient.sendRequest(packetReplayAddress, "quit", null);
	    closeYSession();
	} catch (HornetQException e) {
	    log.warn("Got Exception when quitting the packet replay: ", e);
	}
	notifyStopped();
    }

    private void closeYSession() throws HornetQException {
	yclient.close();
	ysession.close();
    }

    @Override
    public void doStart() {
	try {
	    yclient.executeRpc(packetReplayAddress, "Start", null, null);
	} catch (Exception e) {
	    e.printStackTrace();
	    log.warn("Got Exception when starting the packet replay: ", e);
	}
	notifyStarted();
    }

    @Override
    public void pause() {
	try {
	    yclient.executeRpc(packetReplayAddress, "Pause", null, null);
	} catch (Exception e) {
	    log.warn("Got Exception when pausing the packet replay: ", e);
	}
    }

    @Override
    public void resume() {
	try {
	    yclient.executeRpc(packetReplayAddress, "Resume", null, null);
	} catch (Exception e) {
	    log.warn("Got Exception when resuming the packet replay: ", e);
	}
    }


    @Override
    public void seek(long time) {
	try {
	    yclient.executeRpc(packetReplayAddress, "Seek", Instant.newBuilder().setInstant(time).build(), null);
	} catch (Exception e) {
	    log.warn("Got Exception when seeking the packet replay: ", e);
	}
    }

    @Override
    public void disable() {
    }

    @Override
    public void enable() {
    }

    @Override
    public boolean isDisabled() {
	return false;
    }

    @Override
    public String getDetailedStatus() {
	return "Getting data from "+packetReplayAddress;
    }

    @Override
    public void setParameterListener(ParameterRequestManager parameterRequestManager) {
	this.parameterRequestManager = parameterRequestManager;
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
	if(xtceDb.getParameter(id)!=null) return true;
	else return false;
    }

    @Override
    public boolean canProvide(Parameter p) {
	return xtceDb.getParameterEntries(p)!=null;
    }

    @Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
	Parameter p=xtceDb.getParameter(id);
	if(p==null) throw new InvalidIdentification();
	else return p;
    }

    @Override
    public ReplaySpeed getSpeed() {
	return replayRequest.getSpeed();
    }

    @Override
    public ReplayRequest getReplayRequest() {
	return replayRequest;
    }

    @Override
    public ReplayState getReplayState() {
	if(!isRunning()) {
	    return ReplayState.CLOSED;
	}
	try {
	    ReplayStatus status=(ReplayStatus)yclient.executeRpc(packetReplayAddress, "GetReplayStatus", null, ReplayStatus.newBuilder());
	    return status.getState();
	} catch (Exception e) {
	    e.printStackTrace();
	    log.warn("Got Exception when starting the packet replay: ", e);
	    return ReplayState.ERROR;
	}
    }

    @Override
    public long getDataCount() {
	return dataCount;
    }
}
