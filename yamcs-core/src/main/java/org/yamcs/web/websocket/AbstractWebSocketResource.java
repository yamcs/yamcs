package org.yamcs.web.websocket;

import org.yamcs.Channel;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;

/**
 * A resource bundles a set of logically related operations.
 * Instances of this class are created for every client session separately.
 */
public abstract class AbstractWebSocketResource {

    protected Channel channel;
    protected WebSocketServerHandler wsHandler;


    public AbstractWebSocketResource(Channel channel, WebSocketServerHandler wsHandler) {
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
