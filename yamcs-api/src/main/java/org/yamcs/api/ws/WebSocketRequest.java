package org.yamcs.api.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.io.IOException;

import org.yamcs.protobuf.Websocket.WebSocketClientMessage;

import com.google.protobuf.Message;


/**
 * Tags anything to be sent upstream on an established websocket. Can be extended do add
 * additional structured data.
 */
public class WebSocketRequest {

    private String resource;
    private String operation;

    public WebSocketRequest(String resource, String operation) {
        this.resource = resource;
        this.operation = operation;
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
     * Specify the proto message that dictates how the request data will be serialized. By default
     * this returns null, meaning no request data will be added.
     */
    public Message getRequestData() {
        return null;
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
        return getResource() + "/" + getOperation() + ": " + getRequestData();
    }
}
