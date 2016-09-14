package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorException;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryFilter;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.utils.ValueUtility;

/**
 * Provides realtime command history subscription via web.
 */
public class CommandHistoryResource extends AbstractWebSocketResource implements CommandHistoryConsumer {
    private static final Logger log = LoggerFactory.getLogger(CommandHistoryResource.class);

    private int subscriptionId=-1;

    public CommandHistoryResource(YProcessor channel, WebSocketFrameHandler wsHandler) {
        super(channel, wsHandler);
        wsHandler.addResource("cmdhistory", this);
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authenticationToken) throws WebSocketException {
        switch (ctx.getOperation()) {
        case "subscribe":
            return subscribe(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '"+ctx.getOperation()+"'");
        }
    }

    private WebSocketReplyData subscribe(int requestId) {
        CommandHistoryRequestManager chrm = processor.getCommandHistoryManager();
        if (chrm != null) {
            subscriptionId = chrm.subscribeCommandHistory(null, 0, this);
        }
        return toAckReply(requestId);
    }

    /**
     * called when the socket is closed
     */
    @Override
    public void quit() {
        if(subscriptionId == -1) return;
        CommandHistoryRequestManager chrm = processor.getCommandHistoryManager();
        if (chrm != null) {
            chrm.unsubscribeCommandHistory(subscriptionId);
        }
    }

    public void switchYProcessor(YProcessor c) throws ProcessorException {
        if(subscriptionId == -1) return;

        CommandHistoryRequestManager chrm = processor.getCommandHistoryManager();
        CommandHistoryFilter filter = null;
        if (chrm != null) {
            filter = chrm.unsubscribeCommandHistory(subscriptionId);
        }

        this.processor = c;

        if (processor.hasCommanding()) {
            chrm = processor.getCommandHistoryManager();
            if (filter != null) {
                chrm.addSubscription(filter, this);
            } else {
                chrm.subscribeCommandHistory(null, 0, this);
            }
        }
    }

    @Override
    public void addedCommand(PreparedCommand pc) {
        CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(pc.getCommandId()).addAllAttr(pc.getAttributes()).build();
        doSend(entry);
    }

    @Override
    public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {
        CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder().setName(key).setValue(ValueUtility.toGbp(value)).build();
        CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(cmdId).addAttr(cha).build();
        doSend(entry);
    }


    private void doSend(CommandHistoryEntry entry) {
        try {
            wsHandler.sendData(ProtoDataType.CMD_HISTORY, entry, SchemaCommanding.CommandHistoryEntry.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending command history updates, quitting", e);
            quit();
        }
    }
}
