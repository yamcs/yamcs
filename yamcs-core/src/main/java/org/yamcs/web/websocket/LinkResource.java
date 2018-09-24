package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.management.LinkListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.LinkSubscriptionRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.LinkEvent;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;

/**
 * Provides realtime data-link subscription via web.
 */
public class LinkResource implements WebSocketResource, LinkListener {

    public static final String RESOURCE_NAME = "links";

    private ConnectedWebSocketClient client;

    // Instance requested by the user. This should not update when the processor changes.
    private String instance;

    public LinkResource(ConnectedWebSocketClient client) {
        this.client = client;
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (ctx.getData() != null) {
            LinkSubscriptionRequest req = decoder.decodeMessageData(ctx, LinkSubscriptionRequest.newBuilder()).build();
            if (req.hasInstance()) {
                instance = req.getInstance();
            }
        }

        ManagementService mservice = ManagementService.getInstance();

        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));

        for (LinkInfo linkInfo : mservice.getLinkInfo()) {
            if (instance == null || instance.equals(linkInfo.getInstance())) {
                sendLinkInfo(LinkEvent.Type.REGISTERED, linkInfo);
            }
        }
        mservice.addLinkListener(this);
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        ManagementService mservice = ManagementService.getInstance();
        mservice.removeLinkListener(this);
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
        ManagementService mservice = ManagementService.getInstance();
        mservice.removeLinkListener(this);
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

    private void sendLinkInfo(LinkEvent.Type type, LinkInfo linkInfo) {
        if (instance == null || instance.equals(linkInfo.getInstance())) {
            LinkEvent.Builder linkb = LinkEvent.newBuilder();
            linkb.setType(type);
            linkb.setLinkInfo(linkInfo);
            client.sendData(ProtoDataType.LINK_EVENT, linkb.build());
        }
    }
}
