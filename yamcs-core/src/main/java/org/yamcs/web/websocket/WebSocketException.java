package org.yamcs.web.websocket;

import java.io.IOException;

import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import io.protostuff.Schema;

/**
 * When an exception occurred while handling an incoming web socket request. Used as a one-time response to a client request.
 */
public class WebSocketException extends Exception {
    private static final long serialVersionUID = 1L;

    private int requestId;

    // Optional accompanying data
    // eg. for InvalidIdentification we want to pass the names of the invalid parameters
    private String dataType = "STRING";
    private MessageLite data;
    private Schema<MessageLite> dataSchema;

    /**
     * @param requestId the client request id, if at least this could be successfully interpreted.
     */
    public WebSocketException(int requestId, String message) {
        super(message);
        this.requestId = requestId;
    }

    public WebSocketException(int requestId, Throwable t) {
       super(t.getMessage(), t);
        this.requestId = requestId;
    }

    public WebSocketException(int requestId, String message, Throwable t) {
        super(message + ": " + t.getMessage(), t);
        this.requestId = requestId;
    }

    public int getRequestId() {
        return requestId;
    }

    @SuppressWarnings("unchecked")
    public void attachData(String dataType, MessageLite data, Schema<? extends MessageLite> dataSchema) {
        this.dataType = dataType;
        this.data = data;
        this.dataSchema = (Schema<MessageLite>) dataSchema;
    }

    public String getDataType() {
        return dataType;
    }

    public MessageLite getData() {
        return data;
    }

    public Schema<MessageLite> getDataSchema() {
        return dataSchema;
    }

    /**
     * Converts this exception to a protobuf message
     */
    public WebSocketExceptionData toWebSocketExceptionData() throws IOException {
        WebSocketExceptionData.Builder resultb = WebSocketExceptionData.newBuilder();
        resultb.setProtocolVersion(WSConstants.PROTOCOL_VERSION);
        resultb.setSequenceNumber(requestId);
        resultb.setType(dataType);
        String msg = getMessage();
        if(msg!=null)resultb.setMessage(msg);
        
        if (!dataType.equals("STRING")) {
            try (ByteString.Output out = ByteString.newOutput()) {
                data.writeTo(out);
                out.close();
                resultb.setData(out.toByteString());
            }
        }
        return resultb.build();
    }
}
