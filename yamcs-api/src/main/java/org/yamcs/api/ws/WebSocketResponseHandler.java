package org.yamcs.api.ws;

import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;

/**
 * Handlers the response for one specific message that was sent. Currently
 * doesn't include positive response, since we haven't had a need for it yet
 */
public interface WebSocketResponseHandler {
    void onException(WebSocketExceptionData e);
}
