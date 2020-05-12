package org.yamcs.http.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.http.api.ManagementApi;
import org.yamcs.management.LinkListener;
import org.yamcs.management.LinkManager;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.LinkEvent;
import org.yamcs.protobuf.LinkInfo;
import org.yamcs.protobuf.LinkSubscriptionRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsInstance.InstanceState;

/**
 * Provides realtime data-link subscription via web.
 */
public class LinkResource implements WebSocketResource, LinkListener, ManagementListener {

    private ConnectedWebSocketClient client;

    // Instance requested by the user. This should not update when the processor changes.
    private String instance;

    public LinkResource(ConnectedWebSocketClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "links";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (ctx.getData() != null) {
            LinkSubscriptionRequest req = decoder.decodeMessageData(ctx, LinkSubscriptionRequest.newBuilder()).build();
            if (req.hasInstance()) {
                instance = req.getInstance();
            }
        }
        if (instance != null) {
            YamcsServerInstance ysi = ManagementApi.verifyInstanceObj(instance);
            subscribe(ysi);
        } else {
            ManagementService.getInstance().addManagementListener(this);
            for (YamcsServerInstance ysi : YamcsServer.getInstances()) {
                subscribe(ysi);
            }
        }
        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));
        return null;
    }

    private void subscribe(YamcsServerInstance ysi) {
        LinkManager lmgr = ysi.getLinkManager();
        for (LinkInfo linkInfo : lmgr.getLinkInfo()) {
            sendLinkInfo(LinkEvent.Type.REGISTERED, linkInfo);
        }
        lmgr.addLinkListener(this);
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        unsubscribeAll();
        return WebSocketReply.ack(ctx.getRequestId());
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
        unsubscribeAll();
    }

    private void unsubscribeAll() {
        ManagementService.getInstance().removeManagementListener(this);
        if (instance != null) {
            unsubscribe(ManagementApi.verifyInstanceObj(instance));
        } else {
            for (YamcsServerInstance ysi : YamcsServer.getInstances()) {
                unsubscribe(ysi);
            }
        }
    }

    private void unsubscribe(YamcsServerInstance ysi) {
        LinkManager lmgr = ysi.getLinkManager();
        lmgr.removeLinkListener(this);
    }

    @Override
    public void linkRegistered(LinkInfo linkInfo) {
        if (instance == null || instance.equals(linkInfo.getInstance())) {
            sendLinkInfo(LinkEvent.Type.REGISTERED, linkInfo);
        }
    }

    @Override
    public void linkUnregistered(LinkInfo linkInfo) {
        // TODO Currently not handled correctly by ManagementService

    }

    @Override
    public void linkChanged(LinkInfo linkInfo) {
        if (instance == null || instance.equals(linkInfo.getInstance())) {
            sendLinkInfo(LinkEvent.Type.UPDATED, linkInfo);
        }
    }

    @Override
    public void instanceStateChanged(YamcsServerInstance ysi) {
        if (instance == null && ysi.state() == InstanceState.RUNNING) {
            subscribe(ysi);
        }
    }

    private void sendLinkInfo(LinkEvent.Type type, LinkInfo linkInfo) {
        if (instance == null || instance.equals(linkInfo.getInstance())) {
            LinkEvent.Builder linkb = LinkEvent.newBuilder();
            linkb.setType(type);
            linkb.setLinkInfo(linkInfo);
            client.sendData(ProtoDataType.LINK_EVENT, linkb.build());
        }
    }
}
