package org.yamcs.http.websocket;

import com.google.protobuf.Message;

/**
 * response to a request message
 */
public class WebSocketReply {

    private int requestId;

    // Optional accompanying data
    // eg. for InvalidIdentification we want to pass the names of the invalid parameters
    private String dataType = null;
    private Message data = null;

    /**
     * @param requestId
     *            the client request id, if at least this could be successfully interpreted.
     */
    public WebSocketReply(int requestId) {
        this.requestId = requestId;
    }

    public int getRequestId() {
        return requestId;
    }

    public void attachData(String dataType, Message data) {
        this.dataType = dataType;
        this.data = data;
    }

    public String getDataType() {
        return dataType;
    }

    public Message getData() {
        return data;
    }

    public boolean hasData() {
        return data != null;
    }

    public static WebSocketReply ack(int requestId) {
        return new WebSocketReply(requestId);
    }
}
