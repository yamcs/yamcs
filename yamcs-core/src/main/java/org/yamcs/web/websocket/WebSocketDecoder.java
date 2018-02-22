package org.yamcs.web.websocket;

import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;

/**
 * Used to indicate to the WebSocketServerHandler what type of message can be decoded in a specific media
 * representation.
 * <p>
 * Current implementations need to at least support decoding the envelope, and are assumed to support all operations. In
 * the future, we could break the API in two (1. envelope, 2. payload) if we ever get to a point of needing that.
 */
public interface WebSocketDecoder {

    /**
     * Decodes the common wrapper fields of an incoming web socket message. The actual data can be set
     * implementation-specific and does not necessarily need to be processed here, since a second call will be made with
     * an appropriately determined schema.
     */
    WebSocketDecodeContext decodeMessage(ByteBuf binary) throws WebSocketException;

    /**
     * Decodes any data that may be wrapped by the incoming web socket message
     */
    <T extends Message.Builder> T decodeMessageData(WebSocketDecodeContext ctx, T builder)
            throws WebSocketException;
}
