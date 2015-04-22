package org.yamcs.web.websocket;

import org.yamcs.YProcessor;
import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;

/**
 * A resource bundles a set of logically related operations.
 * Instances of this class are created for every client session separately.
 */
public abstract class AbstractWebSocketResource {

    protected YProcessor channel;
    protected WebSocketServerHandler wsHandler;


    public AbstractWebSocketResource(YProcessor channel, WebSocketServerHandler wsHandler) {
        this.channel = channel;
        this.wsHandler = wsHandler;
    }

    public abstract WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException;

    protected static WebSocketReplyData toAckReply(int requestId) {
        return WebSocketReplyData.newBuilder()
                .setProtocolVersion(WSConstants.PROTOCOL_VERSION)
                .setSequenceNumber(requestId)
                .build();
    }
}
