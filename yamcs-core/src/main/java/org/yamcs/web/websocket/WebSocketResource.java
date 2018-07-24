package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;

/**
 * A resource bundles a set of logically related operations. Instances are created for every client session separately.
 */
public interface WebSocketResource {

    /**
     * Process a request and return a reply. The reply can be null if the implementor of the resource takes care itself
     * of sending the reply - this has been added because the parameterClient wants to send some date data immediately
     * after reply
     */
    WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException;

    void selectProcessor(Processor processor) throws ProcessorException;

    void unselectProcessor();

    /**
     * Called when the web socket is closed
     */
    void socketClosed();
}
