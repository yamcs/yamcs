package org.yamcs.web.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
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
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.SystemPrivilege;

/**
 * Provides realtime command queue subscription via web.
 */
public class CommandQueueResource extends AbstractWebSocketResource implements CommandQueueListener {

    private static final Logger log = LoggerFactory.getLogger(CommandQueueResource.class);
    public static final String RESOURCE_NAME = "cqueues";

    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private volatile boolean subscribed = false;

    public CommandQueueResource(WebSocketClient client) {
        super(client);
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {

        checkSystemPrivilege(ctx.getRequestId(), SystemPrivilege.ControlCommandQueue);

        switch (ctx.getOperation()) {
        case OP_subscribe:
            return subscribe(ctx.getRequestId());
        case OP_unsubscribe:
            return unsubscribe(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReply subscribe(int requestId) throws WebSocketException {
        try {
            WebSocketReply reply = WebSocketReply.ack(requestId);
            wsHandler.sendReply(reply);
            doSubscribe();
            return null;
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
    }

    private WebSocketReply unsubscribe(int requestId) throws WebSocketException {
        doUnsubscribe();
        return WebSocketReply.ack(requestId);
    }

    @Override
    public void quit() {
        doUnsubscribe();
    }

    private void doSubscribe() {
        subscribed = true;
        ManagementService mservice = ManagementService.getInstance();
        CommandQueueManager cqueueManager = mservice.getCommandQueueManager(processor);
        if (cqueueManager != null) {
            cqueueManager.registerListener(this);
            for (CommandQueue q : cqueueManager.getQueues()) {
                sendInitialUpdateQueue(q);
            }
        }
    }

    private void doUnsubscribe() {
        ManagementService mservice = ManagementService.getInstance();
        CommandQueueManager cqueueManager = mservice.getCommandQueueManager(processor);
        if (cqueueManager != null) {
            cqueueManager.removeListener(this);
        }
        subscribed = false;
    }

    @Override
    public void switchProcessor(Processor oldProcessor, Processor newProcessor) throws ProcessorException {
        if (subscribed) {
            doUnsubscribe();
            super.switchProcessor(oldProcessor, newProcessor);
            doSubscribe();
        } else {
            super.switchProcessor(oldProcessor, newProcessor);
        }
    }

    /**
     * right after subcription send the full queeue content (commands included). Afterwards the clinets get notified by
     * command added/command removed when the queue gets modified.
     *
     * @param q
     */
    private void sendInitialUpdateQueue(CommandQueue q) {
        CommandQueueInfo info = ManagementGpbHelper.toCommandQueueInfo(q, true);
        try {
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_INFO, info);
        } catch (Exception e) {
            log.warn("got error when sending command queue info, quitting", e);
            quit();
        }
    }

    @Override
    public void updateQueue(CommandQueue q) {
        CommandQueueInfo info = ManagementGpbHelper.toCommandQueueInfo(q, false);
        try {
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_INFO, info);
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
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build());
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
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build());
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
            wsHandler.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build());
        } catch (Exception e) {
            log.warn("got error when sending command queue event, quitting", e);
            quit();
        }
    }
}
