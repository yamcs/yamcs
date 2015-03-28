package org.yamcs.web.websocket;

import com.dyuproject.protostuff.Schema;
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
     * The source media type that should have been indicated somehow TODO
     */
    String getSupportedMediaType();

    /**
     * Decodes the common wrapper fields of an incoming web socket message.
     *
     * Implementations SHALL NOT close the inputstream, as it could be used for extracting the
     * wrapped data further on.
     */
    WebSocketDecodeContext decodeMessageWrapper(InputStream in) throws WebSocketException;

    /**
     * Decodes the unwrapped contents of an incoming web socket message.
     */
    <T extends MessageLite.Builder> T decodeMessage(WebSocketDecodeContext wrapper, InputStream in, Schema<T> targetSchema) throws WebSocketException;
}
