package org.yamcs.web.websocket;

import org.yamcs.YProcessor;
import org.yamcs.ProcessorException;
import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.security.AuthenticationToken;

/**
 * A resource bundles a set of logically related operations.
 * Instances of this class are created for every client session separately.
 */
public abstract class AbstractWebSocketResource {

    protected YProcessor processor;
    protected WebSocketFrameHandler wsHandler;


    public AbstractWebSocketResource(YProcessor processor, WebSocketFrameHandler wsHandler) {
        this.processor = processor;
        this.wsHandler = wsHandler;
    }

    /**
     * Process a request and return a reply.
     * The reply can be null if the implementor of the resource takes care itself of sending the reply
     *   - this has been added because the parameterClient wants to send some date data immediately after reply
     */
    public abstract WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authToken)
            throws WebSocketException;

    /**
     * Called when the web socket is closed
     */
    public abstract void quit();

    protected static WebSocketReplyData toAckReply(int requestId) {
        return WebSocketReplyData.newBuilder()
                .setProtocolVersion(WSConstants.PROTOCOL_VERSION)
                .setSequenceNumber(requestId)
                .build();
    }

    public void switchYProcessor(YProcessor processor, AuthenticationToken authToken)  throws ProcessorException {
        this.processor = processor;
    }
}
