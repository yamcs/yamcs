package org.yamcs.web.websocket;

import java.io.IOException;

import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.protostuff.Schema;

public interface WebSocketEncoder {

    WebSocketFrame encodeReply(WebSocketReplyData reply) throws IOException;

    WebSocketFrame encodeException(WebSocketException e) throws IOException;

    <T> WebSocketFrame encodeData(int sequenceNumber, ProtoDataType dataType, T message, Schema<T> schema) throws IOException;
}
