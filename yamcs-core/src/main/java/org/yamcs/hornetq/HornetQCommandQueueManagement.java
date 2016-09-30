package org.yamcs.hornetq;

import static org.yamcs.api.artemis.Protocol.CMDQUEUE_CONTROL_ADDRESS;
import static org.yamcs.api.artemis.Protocol.CMDQUEUE_INFO_ADDRESS;
import static org.yamcs.api.artemis.Protocol.HDR_EVENT_NAME;
import static org.yamcs.api.artemis.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REQUEST_TYPE_HEADER_NAME;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueListener;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.CommandQueueRequest;

/**
 * allows controlling command queues via hornet
 * @author nm
 *
 */
public class HornetQCommandQueueManagement implements CommandQueueListener {
    ManagementService managementService;
    YamcsSession ysession;
    YamcsClient yclient;
    YamcsClient queueControlServer;


    static Logger log=LoggerFactory.getLogger(HornetQCommandQueueManagement.class.getName());

    public HornetQCommandQueueManagement(ManagementService managementService) throws YamcsApiException, ActiveMQException {
        this.managementService = managementService;

        if(ysession!=null) return;
        ysession=YamcsSession.newBuilder().build();

        //trick to make sure that the link info queue exists
        yclient=ysession.newClientBuilder().setDataConsumer(CMDQUEUE_INFO_ADDRESS, CMDQUEUE_INFO_ADDRESS).build();
        yclient.close();

        yclient=ysession.newClientBuilder().setDataProducer(true).build();

        queueControlServer =ysession.newClientBuilder().setRpcAddress(CMDQUEUE_CONTROL_ADDRESS).build();

        queueControlServer.rpcConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage message) {
                try {
                    processQueueControlMessage(message);
                } catch (Exception e) {
                    log.error("Error when processing request");
                    e.printStackTrace();
                }
            }
        });
    }

    private void processQueueControlMessage(ClientMessage msg) throws YamcsApiException, ActiveMQException {
        SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
        if(replyto==null) {
            log.warn("Did not receive a replyto header. Ignoring the request");
            return;
        }
        try {
            String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
            log.debug("Received a new request: "+req);
            CommandQueueRequest cqr=(CommandQueueRequest)Protocol.decode(msg, CommandQueueRequest.newBuilder());
            if("setQueueState".equalsIgnoreCase(req)) {
                if(!cqr.hasQueueInfo()) throw new YamcsException("setQueueState requires a queueInfo");
                CommandQueueInfo cqi=cqr.getQueueInfo();
                CommandQueueManager cqm=managementService.getQueueManager(cqi.getInstance(), cqi.getProcessorName());
                cqm.setQueueState(cqi.getName(), cqi.getState());
                queueControlServer.sendReply(replyto, "OK", null);
            } else if("sendCommand".equalsIgnoreCase(req)) {
                if(!cqr.hasQueueEntry()) throw new YamcsException("sendCommand requires a queueEntry");
                CommandQueueEntry cqe=cqr.getQueueEntry();
                CommandQueueManager cqm=managementService.getQueueManager(cqe.getInstance(), cqe.getProcessorName());
                cqm.sendCommand(cqe.getCmdId(), cqr.getRebuild());
                queueControlServer.sendReply(replyto, "OK", null);
            } else if("rejectCommand".equalsIgnoreCase(req)) {
                if(!cqr.hasQueueEntry()) throw new YamcsException("rejectCommand requires a queueEntry");
                CommandQueueEntry cqe=cqr.getQueueEntry();
                CommandQueueManager cqm=managementService.getQueueManager(cqe.getInstance(), cqe.getProcessorName());
                cqm.rejectCommand(cqe.getCmdId(), "username");
                queueControlServer.sendReply(replyto, "OK", null);
            } else  {
                throw new YamcsException("Unknown request '"+req+"'");
            }
        } catch (YamcsException e) {
            log.warn("Sending error reply ", e);
            queueControlServer.sendErrorReply(replyto, e.getMessage());
        } 
    }
    
    /**
     * sends a "commandAdded" event 
     */
    @Override
    public void commandAdded(CommandQueue q, PreparedCommand pc) {
        sendCommandEvent("commandAdded", q, pc, false);
    }

    /**
     * sends a "commandRejected" event 
     */
    @Override
    public void commandRejected(CommandQueue q, PreparedCommand pc) {
        sendCommandEvent("commandRejected", q, pc, true);
    }

    /**
     * sends a "commandSent" event 
     */
    @Override
    public void commandSent(CommandQueue q, PreparedCommand pc) {
        sendCommandEvent("commandSent", q, pc, true);
    }

    private void sendCommandEvent(String eventName, CommandQueue q, PreparedCommand pc, boolean expire) {
        CommandQueueEntry cqe = ManagementGpbHelper.toCommandQueueEntry(q, pc);
        YProcessor c=q.getChannel();
        String lvn=c.getInstance()+"."+c.getName()+"."+pc.getCommandId().getOrigin()+"."+pc.getCommandId().getSequenceNumber();
        ClientMessage msg=ysession.session.createMessage(false);
        msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
        msg.putStringProperty(HDR_EVENT_NAME, eventName);
        if(expire) {
            int oneDay = 24*60*60*1000;
            msg.setExpiration(System.currentTimeMillis()+oneDay);
        }

        Protocol.encode(msg, cqe);
        try {
            yclient.sendData(CMDQUEUE_INFO_ADDRESS, msg);
        } catch (ActiveMQException e) {
            log.error("exception when updating command queue status");
            e.printStackTrace();
        }
    }

    @Override
    public void updateQueue(CommandQueue queue) {
        YProcessor c=queue.getChannel();
        CommandQueueInfo cqi = ManagementGpbHelper.toCommandQueueInfo(queue, false);

        String lvn=c.getInstance()+"."+c.getName()+"."+queue.getName();
        ClientMessage msg=ysession.session.createMessage(false);
        msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
        msg.putStringProperty(HDR_EVENT_NAME, "queueUpdated");
        Protocol.encode(msg, cqi);
        try {
            yclient.sendData(CMDQUEUE_INFO_ADDRESS, msg);
        } catch (ActiveMQException e) {
            log.error("exception when updating command queue status");
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            ysession.close();
        } catch (ActiveMQException e) {
            log.error("Failed to close the yamcs session", e);
        }
    }
}
