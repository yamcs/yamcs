package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;

/**
 * A resource bundles a set of logically related operations. Instances of this class are created for every client
 * session separately.
 */
public abstract class AbstractWebSocketResource {

    protected ConnectedWebSocketClient client;
    protected WebSocketFrameHandler wsHandler;

    public AbstractWebSocketResource(ConnectedWebSocketClient client) {
        this.client = client;
        wsHandler = client.getWebSocketFrameHandler();
    }

    /**
     * Process a request and return a reply. The reply can be null if the implementor of the resource takes care itself
     * of sending the reply - this has been added because the parameterClient wants to send some date data immediately
     * after reply
     */
    public abstract WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException;

    /**
     * Called when the web socket is closed
     */
    public abstract void socketClosed();

    public abstract void selectProcessor(Processor processor) throws ProcessorException;

    public abstract void unselectProcessor();
}
