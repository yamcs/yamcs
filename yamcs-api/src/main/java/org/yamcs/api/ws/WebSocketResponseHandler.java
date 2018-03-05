package org.yamcs.api.ws;

import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;

/**
 * Handlers the response for one specific message that was sent.
 */
public interface WebSocketResponseHandler {
    /**
     * Called when the request has been answered positively
     */
    default public void onCompletion(WebSocketReplyData reply){};
   /**
    * Called when there was an error executing a request
    * @param e
    */
    public void onException(WebSocketExceptionData e);
}
