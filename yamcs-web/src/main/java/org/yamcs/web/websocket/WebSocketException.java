package org.yamcs.web.websocket;

/**
 * Default generic exception, used as a one-time response to a client request.
 */
public class WebSocketException extends Exception {
    private int requestId;

    /**
     * @param requestId the client request id, if at least this could be successfully interpreted.
     */
    public WebSocketException(int requestId, String message) {
        super(message);
        this.requestId = requestId;
    }

    public WebSocketException(Integer requestId, Throwable t) {
        super(t.getMessage(), t);
    }

    public WebSocketException(Integer requestId, String message, Throwable t) {
        super(message + ": " + t.getMessage(), t);
    }

    public int getRequestId() {
        return requestId;
    }
}
