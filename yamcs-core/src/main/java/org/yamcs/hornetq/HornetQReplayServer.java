package org.yamcs.hornetq;

import static org.yamcs.api.artemis.Protocol.DATA_TO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REQUEST_TYPE_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.decode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.*;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.container.ContainerWithId;
import org.yamcs.container.ContainerWithIdConsumer;
import org.yamcs.container.ContainerWithIdRequestHelper;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.Instant;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.parameter.Value;
import org.yamcs.security.HqClientMessageToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.MdbMappings;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.protobuf.ByteString;

/**
 * Provides capability to perform replays via ActiveMQ by initiating new processors.
 * It allows to retrieve parameters via ActiveMQ
 * 
 * @author nm
 *
 */
public class HornetQReplayServer extends AbstractExecutionThreadService {
    static Logger log=LoggerFactory.getLogger(HornetQReplayServer.class);

    final String instance;

    final YamcsClient msgClient;
    final YamcsSession yamcsSession;
    static AtomicInteger count = new AtomicInteger();
    
    public HornetQReplayServer(String instance) throws ActiveMQException, YamcsApiException {
        this.instance = instance;
        yamcsSession = YamcsSession.newBuilder().build();
        msgClient = yamcsSession.newClientBuilder().setRpcAddress(Protocol.getReplayControlAddress(instance)).build();
    }

    @Override
    protected void startUp() {
        Thread.currentThread().setName(this.getClass().getSimpleName()+"["+instance+"]");
    }

    @Override
    public void run() {
        try {
            while(isRunning()) {
                ClientMessage msg=msgClient.rpcConsumer.receive();
                if(msg==null) {
                    if(isRunning()) log.warn("Null message received from the control queue");
                    continue;
                }
                SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
                SimpleString dataAddress=msg.getSimpleStringProperty(DATA_TO_HEADER_NAME);
                if(replyto==null) {
                    if(isRunning()) log.warn("Did not receive a replyto header. Ignoring the request");
                    continue;
                }
                try {
                    String request=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
                    log.debug("received a new request: "+request);
                    if("createReplay".equalsIgnoreCase(request)) {
                        createReplay(msg, replyto, dataAddress);
                    } else  {
                        log.warn("Received unknonw request '"+request+"'");
                        throw new YamcsException("Unknown request '"+request+"'");
                    }
                } catch (YamcsException e) {
                    msgClient.sendErrorReply(replyto, e);
                }
            }
        } catch (Exception e) {
            log.error("Got exception while processing the requests ", e);
        }
    }


    /**
     * create a new packet replay object
     */
    public void createReplay(ClientMessage msg, SimpleString replyto, SimpleString dataAddress) throws Exception {
        ReplayRequest replayRequest=(ReplayRequest)decode(msg, ReplayRequest.newBuilder());
        HqClientMessageToken authToken = null;

        if(replayRequest.hasEventRequest()) {
            String err = "Event replays are not supported (yet) by the HornetqReplay Server"; 
            log.warn(err);
            throw new YamcsException("Unsupported", err);
        }
        if( Privilege.usePrivileges ) {
            Privilege priv = Privilege.getInstance();
            authToken = new HqClientMessageToken(msg, null);

            // Check privileges for requested parameters
            if (replayRequest.hasParameterRequest()) {
                List<NamedObjectId> invalidParameters = new ArrayList<NamedObjectId>();
                for( NamedObjectId noi : replayRequest.getParameterRequest().getNameFilterList() ) {
                    if( ! priv.hasPrivilege(authToken, Privilege.Type.TM_PARAMETER, noi.getName() ) ) {
                        invalidParameters.add( noi );
                    }
                }
                if( ! invalidParameters.isEmpty() ) {
                    NamedObjectList nol=NamedObjectList.newBuilder().addAllList( invalidParameters ).build();
                    log.warn( "Cannot create replay - No privilege for parameters: {}", invalidParameters );
                    throw new YamcsException("InvalidIdentification", "No privilege", nol);
                }
            }

            // Check privileges for requested packets
            if (replayRequest.hasPacketRequest()) {
                Collection<String> allowedPackets = priv.getTmPacketNames(instance, authToken, MdbMappings.MDB_OPSNAME);

                List<NamedObjectId> invalidPackets = new ArrayList<NamedObjectId>();

                for (NamedObjectId noi : replayRequest.getPacketRequest().getNameFilterList()) {
                    if (! allowedPackets.contains(noi.getName())) {
                        invalidPackets.add(noi);
                    }
                }
                if( ! invalidPackets.isEmpty() ) {
                    NamedObjectList nol=NamedObjectList.newBuilder().addAllList( invalidPackets ).build();
                    log.warn( "Cannot create replay - InvalidIdentification for packets: {}", invalidPackets );
                    throw new YamcsException("InvalidIdentification", "Invalid identification", nol);
                }

                // Even when no filter is specified, limit request to authorized packets only
                if (replayRequest.getPacketRequest().getNameFilterList().isEmpty()) {
                    PacketReplayRequest.Builder prr = PacketReplayRequest.newBuilder(replayRequest.getPacketRequest());
                    for (String allowedPacket : allowedPackets) {
                        prr.addNameFilter(NamedObjectId.newBuilder().setName(allowedPacket)
                                .setNamespace(MdbMappings.MDB_OPSNAME));
                    }
                    replayRequest = ReplayRequest.newBuilder(replayRequest).setPacketRequest(prr).build();
                }
            }
           //TODO Command history privilege??
        }
       
        

        ActiveMQReplayListener listener = new ActiveMQReplayListener(dataAddress);
        
        YProcessor yproc = ProcessorFactory.create(instance, "ActiveMQReplay_"+count.incrementAndGet(), "ArchiveRetrieval", "internal", replayRequest);
        listener.yproc = yproc;

        try {
            ParameterWithIdRequestHelper pidrm = new ParameterWithIdRequestHelper(yproc.getParameterRequestManager(), listener);
            pidrm.addRequest(replayRequest.getParameterRequest().getNameFilterList(), authToken);
        } catch (InvalidIdentification e) {
            NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
            if(yproc != null)
                yproc.doStop();
            throw new YamcsException("InvalidIdentification", "Invalid identification", nol);
        }
       
        PacketReplayRequest packetReq = replayRequest.getPacketRequest();
        if(packetReq!=null && packetReq.getNameFilterCount()>0) {
            ContainerWithIdRequestHelper cirh = new ContainerWithIdRequestHelper(yproc.getContainerRequestManager(), listener);
            for(NamedObjectId id: packetReq.getNameFilterList()) {
                cirh.subscribe(id);
            }
        }
        
        
        YProcessor.addProcessorListener(listener);

        StringMessage addr=StringMessage.newBuilder().setMessage(listener.yclient.rpcAddress.toString()).build();
        msgClient.sendReply(replyto,"PACKET_REPLAY_CREATED", addr);
    }

    @Override
    public void triggerShutdown() {
        try {
            msgClient.close();
            yamcsSession.close();
        } catch (ActiveMQException e) {
            log.warn("Got exception when closing the session", e);
        }
    }
    
    
    static class ActiveMQReplayListener implements ProcessorListener, ParameterWithIdConsumer, ContainerWithIdConsumer, CommandHistoryConsumer, MessageHandler {        
        YamcsSession ysession;
        YamcsClient yclient;
        SimpleString dataAddress;
        volatile boolean quitting = false;
        YProcessor yproc;
        
        public ActiveMQReplayListener( SimpleString dataAddress)  throws IOException, ActiveMQException, YamcsException, YamcsApiException {
            this.dataAddress = dataAddress;
            ysession = YamcsSession.newBuilder().build();
            yclient = ysession.newClientBuilder().setRpc(true).setDataProducer(true).build();
            Protocol.killProducerOnConsumerClosed(yclient.getDataProducer(), dataAddress);
            yclient.rpcConsumer.setMessageHandler(this);
        }
        
        
        @Override
        public void onMessage(ClientMessage msg) {
            if(msg==null) {
                if(!quitting) log.warn("null message received from the control queue");
            }

            SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
            if(replyto==null) {
                log.warn("did not receive a replyto header. Ignoring the request");
            }
            try {
                String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);

                log.debug("received a new request: "+req);
                if("Start".equalsIgnoreCase(req)) {
                    yproc.start();
                    yclient.sendReply(replyto, "OK", null);
                } else if("GetReplayStatus".equalsIgnoreCase(req)) {
                    ReplayStatus status=ReplayStatus.newBuilder().setState(yproc.getReplayState()).build();
                    yclient.sendReply(replyto, "REPLY_STATUS", status);
                } else if("Pause".equalsIgnoreCase(req)){
                    yproc.pause();
                    yclient.sendReply(replyto, "OK", null);
                } else if("Resume".equalsIgnoreCase(req)){
                    yproc.start();
                    yclient.sendReply(replyto, "OK", null);
                } else if("Quit".equalsIgnoreCase(req)){
                    quit();
                    yclient.sendReply(replyto, "OK", null);
                } else if("Seek".equalsIgnoreCase(req)){
                    Instant inst=(Instant) decode(msg, Instant.newBuilder());
                    yproc.seek(inst.getInstant());
                    yclient.sendReply(replyto, "OK", null);
                } else  {
                    throw new YamcsException("Unknown request '"+req+"'");
                }
            } catch (YamcsException | IllegalStateException e) { //the illegal state exception will be thrown when starting the processor
                log.warn("sending error reply ", e);
                try {
                    yclient.sendErrorReply(replyto, e.getMessage());
                } catch (ActiveMQException e1) {
                   log.error("Error when sending error reply", e1);
                }

            } catch (Exception e) {
                log.warn("caught exception in packet reply: ", e);
                e.printStackTrace();
            }
        }
        
/*
        @Override
        public void newData(ProtoDataType type, MessageLite data) {
            try {
                yclient.sendData(dataAddress, type, data);
            } catch (ActiveMQException e) {
                log.warn("Got exception when sending data to client", e);
                quit();
            }
        }        
  */      
        public void quit() {
            quitting = true;
            yproc.quit();
        }


        @Override
        public void processorAdded(YProcessor processor) {
        }


        @Override
        public void processorClosed(YProcessor processor) {            
            if(processor!=yproc) return;
            log.debug("processor closed");
            
            sendProcessorState();
            quitting = true;
            YProcessor.removeProcessorListener(this);
            
            try {
                ysession.close();
            } catch (ActiveMQException e) {
                log.warn("Error when closing yclient", e);
            }
        }


        @Override
        public void processorStateChanged(YProcessor processor) {
            if(processor!=yproc) return;
            sendProcessorState();
        }

        private void sendProcessorState() {
            if(quitting) return;
            
            try { 
                ReplayStatus.Builder rsb=ReplayStatus.newBuilder().setState(yproc.getReplayState());
                yclient.sendData(dataAddress, ProtoDataType.STATE_CHANGE, rsb.build());                
            } catch (ActiveMQException e) {
                log.warn("Got exception when signaling state change", e);
                quit();
            }
        }

        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            if(quitting) return;
            ParameterData.Builder pd = ParameterData.newBuilder();
            for(ParameterValueWithId pvwi:params) {
                pd.addParameter(pvwi.getParameterValue().toGpb(pvwi.getId()));
            }
            try {
                yclient.sendData(dataAddress, ProtoDataType.PARAMETER, pd.build());
            } catch (ActiveMQException e) {
                log.warn("Got exception when sending data to client", e);
                quit();
            }
            
        }


        @Override
        public void processContainer(ContainerWithId cwi, ContainerExtractionResult cer) {
            TmPacketData pd = TmPacketData.newBuilder().setId(cwi.getId())
                    .setReceptionTime(cer.getAcquisitionTime())
                    .setGenerationTime(cer.getGenerationTime())
                    .setPacket(ByteString.copyFrom(cer.getContainerContent())).build();
            try {
                yclient.sendData(dataAddress, ProtoDataType.TM_PACKET, pd);
            } catch (ActiveMQException e) {
                log.warn("Got exception when sending data to client", e);
                quit();
            }
        }


        @Override
        public void addedCommand(PreparedCommand pc) {
            if(quitting) return;
            CommandHistoryEntry che = pc.toCommandHistoryEntry();
            try {
                yclient.sendData(dataAddress, ProtoDataType.CMD_HISTORY, che);
            } catch (ActiveMQException e) {
                log.warn("Got exception when sending cmd history data to client", e);
                quit();
            }
        }


        @Override
        public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {
            if(quitting) return;
            CommandHistoryEntry.Builder cheb = CommandHistoryEntry.newBuilder().setCommandId(cmdId);
            cheb.addAttr(CommandHistoryAttribute.newBuilder().setName(key).setTime(changeDate).setValue(ValueUtility.toGbp(value)).build());
            try {
                yclient.sendData(dataAddress, ProtoDataType.CMD_HISTORY, cheb.build());
            } catch (ActiveMQException e) {
                log.warn("Got exception when sending cmd history data to client", e);
                quit();
            }
        }
    }
}
