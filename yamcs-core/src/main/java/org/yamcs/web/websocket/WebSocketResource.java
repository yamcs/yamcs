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
    default WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {

        // FIXME PacketResource still requires the stream name to be encoded in the operation
        String operation = ctx.getOperation();
        String[] parts = operation.split("\\s+");
        if (parts.length == 2 && parts[0].equals("subscribe")) {
            return subscribe(ctx, decoder, parts[1]);
        }

        switch (ctx.getOperation()) {
        case "subscribe":
            return subscribe(ctx, decoder);
        case "unsubscribe":
            return unsubscribe(ctx, decoder);
        case "subscribeAll":
            return subscribeAll(ctx, decoder);
        case "unsubscribeAll":
            return unsubscribeAll(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException;

    WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException;

    // TODO DEPRECATE
    default WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder, String argument)
            throws WebSocketException {
        throw new UnsupportedOperationException();
    }

    // TODO DEPRECATE
    default WebSocketReply subscribeAll(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        throw new UnsupportedOperationException();
    }

    // TODO DEPRECATE
    default WebSocketReply unsubscribeAll(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        throw new UnsupportedOperationException();
    }

    void selectProcessor(Processor processor) throws ProcessorException;

    void unselectProcessor();

    /**
     * Called when the web socket is closed
     */
    void socketClosed();
}
