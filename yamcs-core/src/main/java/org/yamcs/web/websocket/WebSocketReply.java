package org.yamcs.web.websocket;

import java.io.IOException;

import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

/**
 * response to a request message
 */
public class WebSocketReply {

    private int requestId;

    // Optional accompanying data
    // eg. for InvalidIdentification we want to pass the names of the invalid parameters
    private String dataType = null;
    private MessageLite data;

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

    public void attachData(String dataType, MessageLite data) {
        this.dataType = dataType;
        this.data = data;
    }

    public String getDataType() {
        return dataType;
    }

    public MessageLite getData() {
        return data;
    }

    /**
     * Converts this exception to a protobuf message
     */
    public WebSocketReplyData toWebSocketReplyData() throws IOException {
        WebSocketReplyData.Builder resultb = WebSocketReplyData.newBuilder();
        resultb.setProtocolVersion(WSConstants.PROTOCOL_VERSION);
        resultb.setSequenceNumber(requestId);

        if (dataType != null) {
            resultb.setType(dataType);
            try (ByteString.Output out = ByteString.newOutput()) {
                data.writeTo(out);
                out.close();
                resultb.setData(out.toByteString());
            }
        }
        return resultb.build();
    }
}
