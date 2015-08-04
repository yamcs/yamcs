package org.yamcs.web.websocket;

/**
 * The common wrapper fields of messages sent over the websocket
 * The only we still need this if because the javascript handling needs a shared JsonParser to handle processing
 * the wrapper fields first, and the nested data second.
 */
public class WebSocketDecodeContext {

    private int protocolVersion;
    private int messageType;
    private int requestId;
    private String resource;
    private String operation;

    // Could maybe do this better, enables us to maintain our parse state when decoding the payload after the envelope
    // which is useful in particular for jackson.
    private Object data;

    public WebSocketDecodeContext(int protocolVersion, int messageType, int requestId, String resource, String operation) {
        this.protocolVersion = protocolVersion;
        this.messageType = messageType;
        this.requestId = requestId;
        this.resource = resource;
        this.operation = operation;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public int getMessageType() {
        return messageType;
    }

    public int getRequestId() {
        return requestId;
    }

    public String getResource() {
        return resource;
    }

    public String getOperation() {
        return operation;
    }
    
    void setData(Object data) {
        this.data = data;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return requestId + ": [" + resource + "/" + operation + "] ";
    }
}
