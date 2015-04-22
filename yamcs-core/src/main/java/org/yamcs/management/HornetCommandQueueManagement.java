package org.yamcs.management;

import static org.yamcs.api.Protocol.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueListener;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

import com.google.protobuf.ByteString;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.CommandQueueRequest;

/**
 * allows controlling command queues via hornet
 * @author nm
 *
 */
public class HornetCommandQueueManagement implements CommandQueueListener {
    YamcsSession ysession;
    YamcsClient yclient;
    YamcsClient queueControlServer;


    static Logger log=LoggerFactory.getLogger(HornetCommandQueueManagement.class.getName());
    List<CommandQueueManager> qmanagers=new CopyOnWriteArrayList<CommandQueueManager>();

    public HornetCommandQueueManagement() throws YamcsApiException, HornetQException {

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

    public void registerCommandQueueManager(CommandQueueManager cqm) {
	qmanagers.add(cqm);
	cqm.registerListener(this);
	for(CommandQueue q:cqm.getQueues()) {
	    updateQueue(q);
	}
    }


    private void processQueueControlMessage(ClientMessage msg) throws YamcsApiException, HornetQException {
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
		CommandQueueManager cqm=getQueueManager(cqi.getInstance(), cqi.getChannelName());
		cqm.setQueueState(cqi.getName(), cqi.getState(), cqr.getRebuild());
		queueControlServer.sendReply(replyto, "OK", null);
	    } else if("sendCommand".equalsIgnoreCase(req)) {
		if(!cqr.hasQueueEntry()) throw new YamcsException("sendCommand requires a queueEntry");
		CommandQueueEntry cqe=cqr.getQueueEntry();
		CommandQueueManager cqm=getQueueManager(cqe.getInstance(), cqe.getChannelName());
		cqm.sendCommand(cqe.getCmdId(), cqr.getRebuild());
		queueControlServer.sendReply(replyto, "OK", null);
	    } else if("rejectCommand".equalsIgnoreCase(req)) {
		if(!cqr.hasQueueEntry()) throw new YamcsException("rejectCommand requires a queueEntry");
		CommandQueueEntry cqe=cqr.getQueueEntry();
		CommandQueueManager cqm=getQueueManager(cqe.getInstance(), cqe.getChannelName());
		cqm.rejectCommand(cqe.getCmdId(), "username");
		queueControlServer.sendReply(replyto, "OK", null);
	    } else  {
		throw new YamcsException("Unknown request '"+req+"'");
	    }
	} catch (YamcsException e) {
	    e.printStackTrace();
	    log.warn("Sending error reply ", e);
	    queueControlServer.sendErrorReply(replyto, e.getMessage());
	} 
    }

    private CommandQueueManager getQueueManager(String instance, String channelName) throws YamcsException {
	for(int i=0;i<qmanagers.size();i++) {
	    CommandQueueManager cqm=qmanagers.get(i);
	    if(cqm.getInstance().equals(instance) && cqm.getChannelName().equals(channelName)) {
		return cqm;
	    }
	}

	throw new YamcsException("Cannot find a command queue manager for "+instance+"."+channelName);
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
	YProcessor c=q.getChannel();
	CommandQueueEntry cqe=CommandQueueEntry.newBuilder()
		.setInstance(c.getInstance()).setChannelName(c.getName()).setQueueName(q.getName())
		.setCmdId(pc.getCommandId()).setSource(pc.getSource()).setBinary(ByteString.copyFrom(pc.getBinary()))
		.setGenerationTime(pc.getGenerationTime()).setUsername(pc.getUsername()).build();

	String lvn=c.getInstance()+"."+c.getName()+"."+pc.getCommandId().getOrigin()+"."+pc.getCommandId().getSequenceNumber();
	ClientMessage msg=ysession.session.createMessage(false);
	msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
	msg.putStringProperty(HDR_EVENT_NAME, eventName);
	if(expire) {
	    msg.setExpiration(System.currentTimeMillis()+5000);
	}

	Protocol.encode(msg, cqe);
	try {
	    yclient.dataProducer.send(CMDQUEUE_INFO_ADDRESS, msg);
	} catch (HornetQException e) {
	    log.error("exception when updating command queue status");
	    e.printStackTrace();
	}

    }

    @Override
    public void updateQueue(CommandQueue queue) {
	YProcessor c=queue.getChannel();
	CommandQueueInfo cqi=CommandQueueInfo.newBuilder()
		.setInstance(c.getInstance()).setChannelName(c.getName())
		.setName(queue.getName()).setState(queue.getState()).build();

	String lvn=c.getInstance()+"."+c.getName()+"."+queue.getName();
	ClientMessage msg=ysession.session.createMessage(false);
	msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
	msg.putStringProperty(HDR_EVENT_NAME, "queueUpdated");
	Protocol.encode(msg, cqi);
	try {
	    yclient.dataProducer.send(CMDQUEUE_INFO_ADDRESS, msg);
	} catch (HornetQException e) {
	    log.error("exception when updating command queue status");
	    e.printStackTrace();
	}
    }

    public void stop() {
	try {
	    ysession.close();
	} catch (HornetQException e) {
	    log.error("Failed to close the yamcs session", e);
	}
    }
}
