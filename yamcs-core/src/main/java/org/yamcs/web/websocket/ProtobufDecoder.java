package org.yamcs.web.websocket;

import io.protostuff.Schema;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import org.yamcs.protobuf.Websocket.WebSocketClientMessage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes an incoming web socket message using protobuf.
 */
public class ProtobufDecoder implements WebSocketDecoder {

    @Override
    public WebSocketDecodeContext decodeMessage(InputStream in) throws WebSocketException {
        int requestId = WSConstants.NO_REQUEST_ID;
        try {
            WebSocketClientMessage request = WebSocketClientMessage.parseFrom(in);
            if (!request.hasSequenceNumber())
                throw new WebSocketException(requestId, "sequenceNumber must be specified");
            requestId = request.getSequenceNumber();

            if (!request.hasProtocolVersion())
                throw new WebSocketException(requestId, "protocol version must be specified");
            if (request.getProtocolVersion() != WSConstants.PROTOCOL_VERSION)
                throw new WebSocketException(requestId, "Invalid version (expected " + WSConstants.PROTOCOL_VERSION + ", but got " + request.getProtocolVersion());
            if (!request.hasResource())
                throw new WebSocketException(requestId, "resource must be specified");
            if (!request.hasOperation())
                throw new WebSocketException(requestId, "operation must be specified");

            WebSocketDecodeContext ctx = new WebSocketDecodeContext(request.getProtocolVersion(), WSConstants.MESSAGE_TYPE_REQUEST, requestId, request.getResource(), request.getOperation());
            ctx.setData(request.getData());
            return ctx;
        } catch (IOException e) {
            throw new WebSocketException(requestId, e);
        }
    }

    @Override
    public <T extends MessageLite.Builder> T decodeMessageData(WebSocketDecodeContext ctx, Schema<T> dataSchema) throws WebSocketException {
        try {
            T msg = dataSchema.newMessage();
            msg.mergeFrom((ByteString) ctx.getData());
            return msg;
        } catch (IOException e) {
            throw new WebSocketException(ctx.getRequestId(), e);
        }
    }
}
