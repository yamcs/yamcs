package org.yamcs.web.websocket;

import org.yamcs.Channel;

import java.io.InputStream;

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

    public abstract void processRequest(WebSocketDecodeContext ctx, InputStream in) throws WebSocketException;
}
