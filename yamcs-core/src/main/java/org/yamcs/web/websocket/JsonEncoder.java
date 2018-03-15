package org.yamcs.web.websocket;

import java.io.IOException;
import java.io.StringWriter;

import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

import com.google.gson.stream.JsonWriter;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

public class JsonEncoder implements WebSocketEncoder {

    @Override
    public WebSocketFrame encodeReply(WebSocketReply reply) throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonWriter writer = new JsonWriter(sw)) {
            writer.beginArray();
            writer.value(WSConstants.PROTOCOL_VERSION);
            writer.value(WSConstants.MESSAGE_TYPE_REPLY);
            writer.value(reply.getRequestId());
            if(reply.hasData()) {
                writer.beginObject();
                writer.name("type").value(reply.getDataType());
                writer.name("data");
                String json = JsonFormat.printer().print(reply.getData());
                writer.jsonValue(json);
                writer.endObject();
            }
            writer.endArray();
        }
        return new TextWebSocketFrame(sw.toString());
    }

    @Override
    public WebSocketFrame encodeException(WebSocketException e) throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonWriter writer = new JsonWriter(sw)) {
            writer.beginArray();
            writer.value(WSConstants.PROTOCOL_VERSION);
            writer.value(WSConstants.MESSAGE_TYPE_EXCEPTION);
            writer.value(e.getRequestId());

            writer.beginObject();

            writer.name("et");
            writer.value(e.getDataType());

            if ("STRING".equals(e.getDataType())) {
                writer.name("msg");
                writer.value(e.getMessage());
            } else {
                writer.name("msg");
                String json = JsonFormat.printer().print(e.getData());
                writer.jsonValue(json);
            }
            writer.endObject();
            writer.endArray();
        }
        return new TextWebSocketFrame(sw.toString());
    }

    @Override
    public <T extends Message> WebSocketFrame encodeData(int sequenceNumber, ProtoDataType dataType, T message)
            throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonWriter writer = new JsonWriter(sw)) {
            writer.beginArray();
            writer.value(WSConstants.PROTOCOL_VERSION);
            writer.value(WSConstants.MESSAGE_TYPE_DATA);
            writer.value(sequenceNumber);

            writer.beginObject();

            writer.name("dt");
            writer.value(dataType.name());

            writer.name("data");
            String json = JsonFormat.printer().print(message);
            writer.jsonValue(json);

            writer.endObject();
            writer.endArray();
        }
        return new TextWebSocketFrame(sw.toString());
    }
}
