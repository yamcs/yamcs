package org.yamcs.http.websocket;

import java.util.HashSet;
import java.util.Set;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryFilter;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.InvalidCommandId;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.CommandHistorySubscriptionRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;

/**
 * Provides realtime command history subscription via web.
 */
public class CommandHistoryResource implements WebSocketResource, CommandHistoryConsumer {

    private ConnectedWebSocketClient client;

    private volatile boolean subscribed = false;
    private CommandHistoryFilter allSubscription;
    private Set<CommandId> subscribedCommands = new HashSet<>();
    private CommandHistoryRequestManager requestManager;

    public CommandHistoryResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null && processor.hasCommanding()) {
            requestManager = processor.getCommandHistoryManager();
        }
    }

    @Override
    public String getName() {
        return "cmdhistory";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        subscribed = true;

        if (requestManager == null) {
            return WebSocketReply.ack(ctx.getRequestId());
        }

        boolean subscribeAll = true;
        boolean ignorePastCommands = true;

        CommandHistorySubscriptionRequest req = null;
        if (ctx.getData() != null) {
            req = decoder.decodeMessageData(ctx,
                    CommandHistorySubscriptionRequest.newBuilder()).build();
            if (req.hasIgnorePastCommands()) {
                ignorePastCommands = req.getIgnorePastCommands();
            }

            if (req.getCommandIdCount() > 0) {
                subscribeAll = false;
                ignorePastCommands = false;
                client.sendReply(WebSocketReply.ack(ctx.getRequestId()));

                for (CommandId commandId : req.getCommandIdList()) {
                    if (!client.getUser().hasObjectPrivilege(
                            ObjectPrivilegeType.CommandHistory, commandId.getCommandName())) {
                        throw new WebSocketException(ctx.getRequestId(), "Unauthorized");
                    }
                }
                for (CommandId commandId : req.getCommandIdList()) {
                    if (!subscribedCommands.contains(commandId)) {
                        try {
                            CommandHistoryEntry entry = requestManager.subscribeCommand(commandId, this);
                            subscribedCommands.add(commandId);

                            entry = CommandHistoryEntry.newBuilder(entry)
                                    .setGenerationTimeUTC(TimeEncoding.toString(commandId.getGenerationTime()))
                                    .build();
                            client.sendData(ProtoDataType.CMD_HISTORY, entry);

                        } catch (InvalidCommandId e) {
                            WebSocketException ex = new WebSocketException(ctx.getRequestId(), e);
                            ex.attachData("InvalidCommandIdentification", commandId);
                            throw ex;
                        }
                    }
                }
            }
        }

        if (subscribeAll) {
            client.sendReply(WebSocketReply.ack(ctx.getRequestId()));
            long since = ignorePastCommands ? client.getProcessor().getCurrentTime() : 0;
            allSubscription = requestManager.subscribeCommandHistory(null, since, this);
        }

        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (requestManager != null) {
            if (allSubscription != null) {
                requestManager.unsubscribeCommandHistory(allSubscription.subscriptionId);
            }
            for (CommandId commandId : subscribedCommands) {
                requestManager.unsubscribeCommand(commandId, this);
            }
        }
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void unselectProcessor() {
        if (requestManager != null) {
            if (allSubscription != null) {
                requestManager.unsubscribeCommandHistory(allSubscription.subscriptionId);
            }
            for (CommandId commandId : subscribedCommands) {
                requestManager.unsubscribeCommand(commandId, this);
            }
        }
        requestManager = null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        if (subscribed && processor.hasCommanding()) {
            requestManager = processor.getCommandHistoryManager();
            if (requestManager != null) {
                if (allSubscription != null) {
                    requestManager.addSubscription(allSubscription, this);
                } else {
                    long since = client.getProcessor().getCurrentTime();
                    allSubscription = requestManager.subscribeCommandHistory(null, since, this);
                }
            }
        }
    }

    @Override
    public void addedCommand(PreparedCommand pc) {
        if (client.getUser().hasObjectPrivilege(
                ObjectPrivilegeType.CommandHistory, pc.getCommandId().getCommandName())) {
            CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(pc.getCommandId())
                    .setGenerationTimeUTC(TimeEncoding.toString(pc.getCommandId().getGenerationTime()))
                    .addAllAttr(pc.getAttributes())
                    .build();
            client.sendData(ProtoDataType.CMD_HISTORY, entry);
        }
    }

    @Override
    public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {
        if (client.getUser().hasObjectPrivilege(
                ObjectPrivilegeType.CommandHistory, cmdId.getCommandName())) {
            CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder()
                    .setName(key)
                    .setValue(ValueUtility.toGbp(value))
                    .build();
            CommandHistoryEntry entry = CommandHistoryEntry.newBuilder()
                    .setGenerationTimeUTC(TimeEncoding.toString(cmdId.getGenerationTime()))
                    .setCommandId(cmdId)
                    .addAttr(cha)
                    .build();
            client.sendData(ProtoDataType.CMD_HISTORY, entry);
        }
    }

    @Override
    public void socketClosed() {
        if (requestManager != null) {
            if (allSubscription != null) {
                requestManager.unsubscribeCommandHistory(allSubscription.subscriptionId);
            }
            for (CommandId commandId : subscribedCommands) {
                requestManager.unsubscribeCommand(commandId, this);
            }
        }
    }
}
