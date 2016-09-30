package org.yamcs.hornetq;

import static org.yamcs.api.artemis.Protocol.HDR_EVENT_NAME;
import static org.yamcs.api.artemis.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REQUEST_TYPE_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.YPROCESSOR_CONTROL_ADDRESS;
import static org.yamcs.api.artemis.Protocol.YPROCESSOR_INFO_ADDRESS;
import static org.yamcs.api.artemis.Protocol.YPROCESSOR_STATISTICS_ADDRESS;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.api.Constants;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.security.HqClientMessageToken;

/**
 * provides yprocessor management services via hornetq
 * @author nm
 *
 */
public class HornetQProcessorManagement implements ManagementListener {
    YamcsSession ysession;
    YamcsClient yclient, yprocControlServer, linkControlServer;
    static Logger log=LoggerFactory.getLogger(HornetQProcessorManagement.class.getName());
    ManagementService mservice;

    static public final String YPR_createProcessor = "createProcessor";

    public HornetQProcessorManagement(ManagementService mservice) throws YamcsApiException, ActiveMQException {
        this.mservice=mservice;

        if(ysession!=null) return;
        ysession=YamcsSession.newBuilder().build();

        //trick to make sure that the yprocessor info queue exists
        yclient=ysession.newClientBuilder().setDataConsumer(YPROCESSOR_INFO_ADDRESS, YPROCESSOR_INFO_ADDRESS).build();
        yclient.close();

        yclient=ysession.newClientBuilder().setDataProducer(true).build();
        yprocControlServer=ysession.newClientBuilder().setRpcAddress(YPROCESSOR_CONTROL_ADDRESS).build();
        yprocControlServer.rpcConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage message) {
                try {
                    processControlMessage(message);
                } catch (Exception e) {
                    log.error("Error when processing request");
                    e.printStackTrace();
                }
            }
        });
    }



    private void processControlMessage(ClientMessage msg) throws YamcsApiException, ActiveMQException {
        HqClientMessageToken usertoken= new HqClientMessageToken(msg, null);

        SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
        if(replyto==null) {
            log.warn("Did not receive a replyto header. Ignoring the request");
            return;
        }
        try {
            String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
            log.debug("Received a new request: "+req);
            if(Constants.YPR_createProcessor.equalsIgnoreCase(req)) {
                ProcessorManagementRequest cr=(ProcessorManagementRequest)Protocol.decode(msg, ProcessorManagementRequest.newBuilder());
                mservice.createProcessor(cr, usertoken);
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if(Constants.YPR_connectToProcessor.equalsIgnoreCase(req)) {
                ProcessorManagementRequest cr=(ProcessorManagementRequest)Protocol.decode(msg, ProcessorManagementRequest.newBuilder());
                mservice.connectToProcessor(cr, usertoken);
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if(Constants.YPR_pauseReplay.equalsIgnoreCase(req)) {
                ProcessorRequest cr=(ProcessorRequest)Protocol.decode(msg, ProcessorRequest.newBuilder());
                YProcessor c=YProcessor.getInstance(cr.getInstance(), cr.getName());
                c.pause();
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if(Constants.YPR_resumeReplay.equalsIgnoreCase(req)) {
                ProcessorRequest cr=(ProcessorRequest)Protocol.decode(msg, ProcessorRequest.newBuilder());
                YProcessor c=YProcessor.getInstance(cr.getInstance(), cr.getName());
                c.resume();
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if(Constants.YPR_seekReplay.equalsIgnoreCase(req)) {
                ProcessorRequest cr=(ProcessorRequest)Protocol.decode(msg, ProcessorRequest.newBuilder());
                YProcessor c=YProcessor.getInstance(cr.getInstance(), cr.getName());
                if(!cr.hasSeekTime()) throw new YamcsException("seekReplay requested without a seektime");
                c.seek(cr.getSeekTime());
                yprocControlServer.sendReply(replyto, "OK", null);
            } else  {
                throw new YamcsException("Unknown request '"+req+"'");
            }
        } catch (YamcsException e) {
            log.warn("Sending error reply "+ e);
            yprocControlServer.sendErrorReply(replyto, e.getMessage());
        }
    }


    @Override
    public void processorAdded(ProcessorInfo processorInfo) {
        try {
            sendEvent("yprocUpdated", processorInfo, false);
        } catch (Exception e) {
            log.error("Exception when registering yproc: ", e);
        }
    }

    @Override
    public void processorClosed(ProcessorInfo processorInfo) {
        sendEvent("yprocClosed", processorInfo, true);
    }

    @Override
    public void processorStateChanged(ProcessorInfo processorInfo) {
        try {
            sendEvent("yprocUpdated", processorInfo, false);
        } catch (Exception e) {
            log.error("Exception when sending yprocUpdated event: ", e);
        }
    }

    private void sendEvent(String eventName, ProcessorInfo ci, boolean expire) {
        ClientMessage msg=ysession.session.createMessage(false);
        String lvn=ci.getInstance()+"."+ci.getName();
        msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
        msg.putStringProperty(HDR_EVENT_NAME, eventName);
        if(expire) {
            msg.setExpiration(System.currentTimeMillis()+5000);
        }
        Protocol.encode(msg, ci);
        try {
            yclient.sendData(YPROCESSOR_INFO_ADDRESS, msg);
        } catch (ActiveMQException e) {
            log.error("Exception when sending yproc event: ", e);
        }
    }

    @Override
    public void statisticsUpdated(YProcessor yproc, Statistics stats) {
        try {
            ClientMessage msg=ysession.session.createMessage(false);
            String lvn=yproc.getInstance()+"."+yproc.getName();
            msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
            msg.setExpiration(System.currentTimeMillis()+2000);
            Protocol.encode(msg, stats);
            yclient.sendData(YPROCESSOR_STATISTICS_ADDRESS, msg);
        } catch (ActiveMQException e){
            log.error("got exception when sending the yproc processing statistics: ", e);
        }
    }

    @Override
    public void clientRegistered(ClientInfo ci) {
        sendClientEvent("clientUpdated", ci, false);
    }

    @Override
    public void clientUnregistered(ClientInfo ci) {
        sendClientEvent("clientDisconnected", ci, true);
    }

    @Override
    public void clientInfoChanged(ClientInfo ci) {
        sendClientEvent("clientUpdated", ci, false);
    }

    static int x=0;
    private void sendClientEvent(String eventName, ClientInfo ci, boolean expire){
        ClientMessage msg=ysession.session.createMessage(false);
        String lvn="Client "+ci.getId();
        x++;
        msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
        msg.putStringProperty(HDR_EVENT_NAME, eventName);
        if(expire) {
            msg.setExpiration(System.currentTimeMillis()+5000);
        }
        Protocol.encode(msg, ci);
        try {
            yclient.sendData(YPROCESSOR_INFO_ADDRESS, msg);
        } catch (ActiveMQException e) {
            log.error("exception when sedning client event: ", e);
        }
    }

    public void close() {
        try {
            ysession.close();
        } catch (ActiveMQException e) {
            log.error("Failed to close the yamcs session", e);
        }
    }
}
