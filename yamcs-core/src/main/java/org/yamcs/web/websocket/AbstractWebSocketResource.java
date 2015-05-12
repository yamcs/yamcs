package org.yamcs.web.websocket;

import org.yamcs.YProcessor;
import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.security.AuthenticationToken;

/**
 * A resource bundles a set of logically related operations.
 * Instances of this class are created for every client session separately.
 */
public abstract class AbstractWebSocketResource {

    protected YProcessor yproc;
    protected WebSocketServerHandler wsHandler;


    public AbstractWebSocketResource(YProcessor yproc, WebSocketServerHandler wsHandler) {
        this.yproc = yproc;
        this.wsHandler = wsHandler;
    }

    /**
     * Process a request and return a reply.
     * The reply can be null if the implementor of the resource takes care itself of sending the reply 
     *   - this has been added because the parameterClient wants to send immediately after the replay some data
     * 
     * @param ctx
     * @param decoder
     * @return
     * @throws WebSocketException
     */
    public abstract WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authToken)
            throws WebSocketException;

    protected static WebSocketReplyData toAckReply(int requestId) {
        return WebSocketReplyData.newBuilder()
                .setProtocolVersion(WSConstants.PROTOCOL_VERSION)
                .setSequenceNumber(requestId)
                .build();
    }
}
