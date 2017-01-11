package org.yamcs.api.ws;

import java.io.IOException;

import org.yamcs.protobuf.Web.WebSocketClientMessage;

import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;


/**
 * Tags anything to be sent upstream on an established websocket. Can be extended do add
 * additional structured data.
 */
public class WebSocketRequest {

    private String resource;
    private String operation;
    private Message requestData;

    public WebSocketRequest(String resource, String operation) {
        this.resource = resource;
        this.operation = operation;
    }

    public WebSocketRequest(String resource, String operation, Message requestData) {
        this.resource = resource;
        this.operation = operation;
        this.requestData = requestData;
    }

    /**
     * @return the type of the resource.
     */
    public String getResource() {
        return resource;
    }

    /**
     * @return the operation on the resource
     */
    public String getOperation() {
        return operation;
    }

    /**
     * @return the proto message that dictates how the request data (if any) will be serialized
     */
    public Message getRequestData() {
        return requestData;
    }

    WebSocketFrame toWebSocketFrame(int seqId) {
        WebSocketClientMessage.Builder msg = WebSocketClientMessage.newBuilder();
        msg.setProtocolVersion(WSConstants.PROTOCOL_VERSION);
        msg.setSequenceNumber(seqId);
        msg.setResource(getResource());
        msg.setOperation(getOperation());

        Message data = getRequestData();
        if (data != null)
            msg.setData(data.toByteString());

        ByteBuf buf = Unpooled.buffer();
        try (ByteBufOutputStream bout = new ByteBufOutputStream(buf)) {
            msg.build().writeTo(bout);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new BinaryWebSocketFrame(buf);
    }

    @Override
    public String toString() {
        return getResource() + "/" + getOperation() + ((requestData==null)? "": ": " + getRequestData());
    }
}
