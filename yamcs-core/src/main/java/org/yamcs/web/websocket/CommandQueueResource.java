package org.yamcs.web.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorException;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueListener;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueEvent;
import org.yamcs.protobuf.Commanding.CommandQueueEvent.Type;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;

/**
 * Provides realtime command queue subscription via web.
 */
public class CommandQueueResource extends AbstractWebSocketResource implements CommandQueueListener {
    Logger log;

    public CommandQueueResource(YProcessor processor, WebSocketServerHandler wsHandler) {
        super(processor, wsHandler);
        log = LoggerFactory.getLogger(CommandQueueResource.class.getName() + "[" + processor.getInstance() + "]");
        wsHandler.addResource("cqueues", this);
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authenticationToken) throws WebSocketException {
        switch (ctx.getOperation()) {
        case "subscribe":
            return subscribe(ctx.getRequestId());
        case "unsubscribe":
            return unsubscribe(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '"+ctx.getOperation()+"'");
        }
    }

    private WebSocketReplyData subscribe(int requestId) throws WebSocketException {
        try {
            WebSocketReplyData reply = toAckReply(requestId);
            wsHandler.sendReply(reply);
            doSubscribe();
            return null;
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
    }
    
    private WebSocketReplyData unsubscribe(int requestId) throws WebSocketException {
        doUnsubscribe();
        return toAckReply(requestId);
    }

    @Override
    public void quit() {
        doUnsubscribe();
    }
    
    private void doSubscribe() {
        ManagementService mservice = ManagementService.getInstance();
        CommandQueueManager cqueueManager = mservice.getCommandQueueManager(processor);
        if (cqueueManager != null) {
            cqueueManager.registerListener(this);
            for (CommandQueue q : cqueueManager.getQueues()) {
                updateQueue(q);
            }
        }
    }
    
    private void doUnsubscribe() {
        ManagementService mservice = ManagementService.getInstance();
        CommandQueueManager cqueueManager = mservice.getCommandQueueManager(processor);
        if (cqueueManager != null) {
            cqueueManager.removeListener(this);
        }
    }

    @Override
    public void switchYProcessor(YProcessor newProcessor, AuthenticationToken authToken) throws YProcessorException {
        doUnsubscribe();
        processor = newProcessor;
        doSubscribe();
    }
    
    @Override
    public void updateQueue(CommandQueue q) {
        CommandQueueInfo info = ManagementGpbHelper.toCommandQueueInfo(q);
        try {
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_INFO, info, SchemaCommanding.CommandQueueInfo.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending command queue info, quitting", e);
            quit();
        }
    }

    @Override
    public void commandAdded(CommandQueue q, PreparedCommand pc) {
        CommandQueueEntry data = ManagementGpbHelper.toCommandQueueEntry(q, pc);
        try {
            CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
            evtb.setType(Type.COMMAND_ADDED);
            evtb.setData(data);
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build(), SchemaCommanding.CommandQueueEvent.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending command queue event, quitting", e);
            quit();
        }
    }

    @Override
    public void commandRejected(CommandQueue q, PreparedCommand pc) {
        CommandQueueEntry data = ManagementGpbHelper.toCommandQueueEntry(q, pc);
        try {
            CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
            evtb.setType(Type.COMMAND_REJECTED);
            evtb.setData(data);
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build(), SchemaCommanding.CommandQueueEvent.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending command queue event, quitting", e);
            quit();
        }
    }

    @Override
    public void commandSent(CommandQueue q, PreparedCommand pc) {
        CommandQueueEntry data = ManagementGpbHelper.toCommandQueueEntry(q, pc);
        try {
            CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
            evtb.setType(Type.COMMAND_SENT);
            evtb.setData(data);
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build(), SchemaCommanding.CommandQueueEvent.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending command queue event, quitting", e);
            quit();
        }   
    }
}
