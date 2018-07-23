package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.security.SystemPrivilege;

import io.netty.channel.Channel;

/**
 * A resource bundles a set of logically related operations. Instances of this class are created for every client
 * session separately.
 */
public abstract class AbstractWebSocketResource {
    protected Processor processor;
    protected WebSocketClient client;
    protected WebSocketFrameHandler wsHandler;

    public AbstractWebSocketResource(WebSocketClient client) {
        this.client = client;
        this.processor = client.getProcessor();
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
    public abstract void quit();

    public void switchProcessor(Processor oldProcessor, Processor newProcessor) throws ProcessorException {
        this.processor = newProcessor;
    }

    public Channel getChannel() {
        return wsHandler.getChannel();
    }

    protected void checkSystemPrivilege(int requestId, SystemPrivilege systemPrivilege) throws WebSocketException {
        if (!client.getUser().hasSystemPrivilege(systemPrivilege)) {
            throw new WebSocketException(requestId, "Need " + systemPrivilege + " privilege for this operation");
        }
    }
}
