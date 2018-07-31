package org.yamcs.web.websocket;

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
public class CommandQueueResource implements WebSocketResource, CommandQueueListener {

    public static final String RESOURCE_NAME = "cqueues";

    private ConnectedWebSocketClient client;

    private volatile boolean subscribed = false;

    private CommandQueueManager commandQueueManager;

    public CommandQueueResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null) {
            ManagementService mservice = ManagementService.getInstance();
            commandQueueManager = mservice.getCommandQueueManager(processor);
        }
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        client.checkSystemPrivilege(ctx.getRequestId(), SystemPrivilege.ControlCommandQueue);
        WebSocketReply reply = WebSocketReply.ack(ctx.getRequestId());
        client.sendReply(reply);

        subscribed = true;
        if (commandQueueManager != null) {
            commandQueueManager.registerListener(this);
            for (CommandQueue q : commandQueueManager.getQueues()) {
                sendInitialUpdateQueue(q);
            }
        }
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (commandQueueManager != null) {
            commandQueueManager.removeListener(this);
        }
        subscribed = false;
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void unselectProcessor() {
        if (commandQueueManager != null) {
            commandQueueManager.removeListener(this);
        }
        commandQueueManager = null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        ManagementService mservice = ManagementService.getInstance();
        commandQueueManager = mservice.getCommandQueueManager(processor);
        if (subscribed && commandQueueManager != null) {
            commandQueueManager.registerListener(this);
            for (CommandQueue q : commandQueueManager.getQueues()) {
                sendInitialUpdateQueue(q);
            }
        }
    }

    /**
     * right after subcription send the full queue content (commands included). Afterwards the clients get notified by
     * command added/command removed when the queue gets modified.
     */
    private void sendInitialUpdateQueue(CommandQueue q) {
        CommandQueueInfo info = ManagementGpbHelper.toCommandQueueInfo(q, true);
        client.sendData(ProtoDataType.COMMAND_QUEUE_INFO, info);
    }

    @Override
    public void updateQueue(CommandQueue q) {
        CommandQueueInfo info = ManagementGpbHelper.toCommandQueueInfo(q, false);
        client.sendData(ProtoDataType.COMMAND_QUEUE_INFO, info);
    }

    @Override
    public void commandAdded(CommandQueue q, PreparedCommand pc) {
        CommandQueueEntry data = ManagementGpbHelper.toCommandQueueEntry(q, pc);
        CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
        evtb.setType(Type.COMMAND_ADDED);
        evtb.setData(data);
        client.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build());
    }

    @Override
    public void commandRejected(CommandQueue q, PreparedCommand pc) {
        CommandQueueEntry data = ManagementGpbHelper.toCommandQueueEntry(q, pc);
        CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
        evtb.setType(Type.COMMAND_REJECTED);
        evtb.setData(data);
        client.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build());
    }

    @Override
    public void commandSent(CommandQueue q, PreparedCommand pc) {
        CommandQueueEntry data = ManagementGpbHelper.toCommandQueueEntry(q, pc);
        CommandQueueEvent.Builder evtb = CommandQueueEvent.newBuilder();
        evtb.setType(Type.COMMAND_SENT);
        evtb.setData(data);
        client.sendData(ProtoDataType.COMMAND_QUEUE_EVENT, evtb.build());
    }

    @Override
    public void socketClosed() {
        if (commandQueueManager != null) {
            commandQueueManager.removeListener(this);
        }
    }
}
