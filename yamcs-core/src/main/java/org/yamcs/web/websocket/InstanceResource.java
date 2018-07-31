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
public class InstanceResource implements WebSocketResource, ManagementListener {

    public static final String RESOURCE_NAME = "instance";

    private ConnectedWebSocketClient client;

    private volatile boolean subscribed;

    public InstanceResource(ConnectedWebSocketClient client) {
        this.client = client;
    }

    /**
     * Registers for updates on any processor or client. Sends the current set of processor, and clients (in that order)
     * to the requester.
     */
    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        client.sendReply(new WebSocketReply(ctx.getRequestId()));
        ManagementService.getInstance().addManagementListener(this);
        subscribed = true;
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        ManagementService.getInstance().removeManagementListener(this);
        client.sendReply(new WebSocketReply(ctx.getRequestId()));
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
            client.sendData(ProtoDataType.INSTANCE, instanceInfo);
        }
    }
}
