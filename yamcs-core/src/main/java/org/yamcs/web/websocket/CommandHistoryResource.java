package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryFilter;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.ValueUtility;

/**
 * Provides realtime command history subscription via web.
 */
public class CommandHistoryResource implements WebSocketResource, CommandHistoryConsumer {

    public static final String RESOURCE_NAME = "cmdhistory";

    private ConnectedWebSocketClient client;

    private CommandHistoryFilter subscription;
    private CommandHistoryRequestManager commandHistoryRequestManager;

    public CommandHistoryResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null && processor.hasCommanding()) {
            commandHistoryRequestManager = processor.getCommandHistoryManager();
        }
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (commandHistoryRequestManager != null) {
            subscription = commandHistoryRequestManager.subscribeCommandHistory(null, 0, this);
        }
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        return null;
    }

    @Override
    public void unselectProcessor() {
        if (subscription != null) {
            if (commandHistoryRequestManager != null) {
                commandHistoryRequestManager.unsubscribeCommandHistory(subscription.subscriptionId);
            }
        }
        commandHistoryRequestManager = null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        if (processor.hasCommanding()) {
            commandHistoryRequestManager = processor.getCommandHistoryManager();
            if (commandHistoryRequestManager != null && subscription != null) {
                commandHistoryRequestManager.addSubscription(subscription, this);
            }
        }
    }

    @Override
    public void addedCommand(PreparedCommand pc) {
        CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(pc.getCommandId())
                .addAllAttr(pc.getAttributes()).build();
        client.sendData(ProtoDataType.CMD_HISTORY, entry);
    }

    @Override
    public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {
        CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder().setName(key)
                .setValue(ValueUtility.toGbp(value)).build();
        CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(cmdId).addAttr(cha).build();
        client.sendData(ProtoDataType.CMD_HISTORY, entry);
    }

    @Override
    public void socketClosed() {
        if (subscription != null && commandHistoryRequestManager != null) {
            commandHistoryRequestManager.unsubscribeCommandHistory(subscription.subscriptionId);
        }
    }
}
