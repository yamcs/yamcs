package org.yamcs.api.ws;

import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;

/**
 * Use by the WebSocketClient for the exceptions received via the websocket interface
 * 
 * @author nm
 *
 */
@SuppressWarnings("serial")
public class WebSocketExecutionException extends Exception {

    private final WebSocketExceptionData exceptionData;

    public WebSocketExecutionException(WebSocketExceptionData exceptionData) {
        super(exceptionData.toString());
        this.exceptionData = exceptionData;
    }

    public WebSocketExceptionData getExceptionData() {
        return exceptionData;
    }
}
