package org.yamcs.web.websocket;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.ProtoDataType;

import com.google.protobuf.Message;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;

public interface WebSocketEncoder {

    WebSocketFrame encodeReply(WebSocketReply reply) throws IOException;

    WebSocketFrame encodeException(WebSocketException e) throws IOException;

    <T extends Message> WebSocketFrame encodeData(int sequenceNumber, ProtoDataType dataType, T message)
            throws IOException;
}
