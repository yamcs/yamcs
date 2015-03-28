package org.yamcs.web.websocket;

import com.dyuproject.protostuff.Schema;
import com.google.protobuf.MessageLite;

/**
 * WebSocketException that carries a structured message as its payload.
 * Useful for passing interpretable information back to the client.
 * <p>
 * We could have written this better..... if only java allowed for
 * generic subclasses of Throwable. boo.
 */
public class StructuredWebSocketException extends WebSocketException {

    private MessageLite data;
    private Schema<MessageLite> dataSchema;

    public StructuredWebSocketException(int requestId, String messageType, MessageLite data, Schema<? extends MessageLite> dataSchema) {
        super(requestId, messageType);
        this.data = data;
        this.dataSchema = (Schema<MessageLite>) dataSchema;
    }

    public MessageLite getData() {
        return data;
    }

    public Schema<MessageLite> getSchema() {
        return dataSchema;
    }
}
