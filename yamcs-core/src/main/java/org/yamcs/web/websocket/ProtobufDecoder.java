package org.yamcs.web.websocket;

import java.io.IOException;
import java.io.InputStream;

import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Web.WebSocketClientMessage;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

/**
 * Decodes an incoming web socket message using protobuf.
 */
public class ProtobufDecoder implements WebSocketDecoder {

    @Override
    public WebSocketDecodeContext decodeMessage(ByteBuf binary) throws WebSocketException {
        int requestId = WSConstants.NO_REQUEST_ID;
        try (InputStream in = new ByteBufInputStream(binary)) {
            WebSocketClientMessage request = WebSocketClientMessage.parseFrom(in);
            if (!request.hasSequenceNumber()) {
                throw new WebSocketException(requestId, "sequenceNumber must be specified");
            }
            requestId = request.getSequenceNumber();

            if (!request.hasProtocolVersion()) {
                throw new WebSocketException(requestId, "protocol version must be specified");
            }
            if (request.getProtocolVersion() != WSConstants.PROTOCOL_VERSION) {
                throw new WebSocketException(requestId, "Invalid version (expected " + WSConstants.PROTOCOL_VERSION
                        + ", but got " + request.getProtocolVersion());
            }
            if (!request.hasResource()) {
                throw new WebSocketException(requestId, "resource must be specified");
            }
            if (!request.hasOperation()) {
                throw new WebSocketException(requestId, "operation must be specified");
            }

            WebSocketDecodeContext ctx = new WebSocketDecodeContext(request.getProtocolVersion(),
                    WSConstants.MESSAGE_TYPE_REQUEST, requestId, request.getResource(), request.getOperation());
            if (request.hasData()) {
                ctx.setData(request.getData());
            }
            return ctx;
        } catch (IOException e) {
            throw new WebSocketException(requestId, e);
        }
    }

    @Override
    public <T extends Message.Builder> T decodeMessageData(WebSocketDecodeContext ctx, T builder)
            throws WebSocketException {
        try {
            builder.mergeFrom((ByteString) ctx.getData());
            return builder;
        } catch (IOException e) {
            throw new WebSocketException(ctx.getRequestId(), e);
        }
    }
}
