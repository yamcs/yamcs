package org.yamcs.web.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServerInstance;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;

/**
 * Provides lifecycle updates on one or all instances.
 */
public class InstanceResource extends AbstractWebSocketResource implements ManagementListener {

    private static final Logger log = LoggerFactory.getLogger(InstanceResource.class);
    public static final String RESOURCE_NAME = "instance";
    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private volatile boolean subscribed;

    public InstanceResource(WebSocketClient client) {
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

        try {
            wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        ManagementService.getInstance().addManagementListener(this);
        subscribed = true;
        return null;
    }

    private WebSocketReply processUnsubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        ManagementService.getInstance().removeManagementListener(this);
        try {
            wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        return null;
    }

    @Override
    public void quit() {
        ManagementService.getInstance().removeManagementListener(this);
        subscribed = false;
    }

    @Override
    public void instanceStateChanged(YamcsServerInstance ysi) {
        if (subscribed) {
            YamcsInstance instanceInfo = ysi.getInstanceInfo();
            try {
                wsHandler.sendData(ProtoDataType.INSTANCE, instanceInfo);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }
}
