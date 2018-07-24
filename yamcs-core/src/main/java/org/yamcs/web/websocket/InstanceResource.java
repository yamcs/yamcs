package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.YamcsServerInstance;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;

/**
 * Provides lifecycle updates on one or all instances.
 */
public class InstanceResource extends AbstractWebSocketResource implements ManagementListener {

    public static final String RESOURCE_NAME = "instance";
    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private volatile boolean subscribed;

    public InstanceResource(ConnectedWebSocketClient client) {
        super(client);
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        case OP_unsubscribe:
            return processUnsubscribeRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    /**
     * Registers for updates on any processor or client. Sends the current set of processor, and clients (in that order)
     * to the requester.
     */
    private WebSocketReply processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {

        wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
        ManagementService.getInstance().addManagementListener(this);
        subscribed = true;
        return null;
    }

    private WebSocketReply processUnsubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        ManagementService.getInstance().removeManagementListener(this);
        wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
        return null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        // Ignore
    }

    @Override
    public void unselectProcessor() {
        // Ignore
    }

    @Override
    public void socketClosed() {
        ManagementService.getInstance().removeManagementListener(this);
        subscribed = false;
    }

    @Override
    public void instanceStateChanged(YamcsServerInstance ysi) {
        if (subscribed) {
            YamcsInstance instanceInfo = ysi.getInstanceInfo();
            wsHandler.sendData(ProtoDataType.INSTANCE, instanceInfo);
        }
    }
}
