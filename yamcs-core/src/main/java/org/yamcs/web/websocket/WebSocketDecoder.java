package org.yamcs.web.websocket;

import io.protostuff.Schema;
import com.google.protobuf.MessageLite;

import java.io.InputStream;

/**
 * Used to indicate to the WebSocketServerHandler what type of message can be decoded in a specific
 * media representation.
 * <p>
 * Current implementations need to at least support decoding the envelope, and are assumed to support
 * all operations. In the future, we could break the API in two (1. envelope, 2. payload) if we ever
 * get to a point of needing that.
 */
public interface WebSocketDecoder {

    /**
     * Decodes the common wrapper fields of an incoming web socket message.
     * The actual data can be set implementation-specific and does not necessarily need to
     * be processed here, since a second call will be made with an appropriately determined
     * schema.
     */
    WebSocketDecodeContext decodeMessage(InputStream in) throws WebSocketException;

    /**
     * Decodes any data that may be wrapped by the incoming web socket message
     */
    <T extends MessageLite.Builder> T decodeMessageData(WebSocketDecodeContext ctx, Schema<T> dataSchema) throws WebSocketException;
}
