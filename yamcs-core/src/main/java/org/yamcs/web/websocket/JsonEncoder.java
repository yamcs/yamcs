package org.yamcs.web.websocket;

import java.io.IOException;
import java.io.StringWriter;

import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

public class JsonEncoder implements WebSocketEncoder {

    private JsonFactory jsonFactory = new JsonFactory();

    @Override
    public WebSocketFrame encodeReply(WebSocketReplyData reply) throws IOException {
        StringWriter sw=new StringWriter();
        
        JsonGenerator g=jsonFactory.createGenerator(sw);
        writeMessageStart(g, WSConstants.MESSAGE_TYPE_REPLY, reply.getSequenceNumber());
        writeMessageEnd(g);
        return new TextWebSocketFrame(sw.toString());
    }

    @Override
    public WebSocketFrame encodeException(WebSocketException e) throws IOException {
        StringWriter sw = new StringWriter();
        JsonGenerator g = jsonFactory.createGenerator(sw);
        writeMessageStart(g, WSConstants.MESSAGE_TYPE_EXCEPTION, e.getRequestId());
        g.writeStartObject();
        if (e.getDataType().equals("STRING")) {
            g.writeStringField("et", "STRING");
            g.writeStringField("msg", e.getMessage());
        } else {
            g.writeStringField("et", e.getDataType());
            g.writeFieldName("msg");
            JsonIOUtil.writeTo(g, e.getData(), e.getDataSchema(), false);
        }
        g.writeEndObject();
        writeMessageEnd(g);
        return new TextWebSocketFrame(sw.toString());
    }

    @Override
    public <T> WebSocketFrame encodeData(int sequenceNumber, ProtoDataType dataType, T message, Schema<T> schema) throws IOException {
        StringWriter sw=new StringWriter();
        JsonGenerator g=jsonFactory.createGenerator(sw);
        writeMessageStart(g, WSConstants.MESSAGE_TYPE_DATA, sequenceNumber);
        g.writeStartObject();
        g.writeStringField("dt", dataType.name());
        g.writeFieldName("data");
        JsonIOUtil.writeTo(g, message, schema, false);
        g.writeEndObject();
        writeMessageEnd(g);
        return new TextWebSocketFrame(sw.toString());
    }

    private void writeMessageStart(JsonGenerator g, int messageType, int seqId) throws IOException {
        g.writeStartArray();
        g.writeNumber(WSConstants.PROTOCOL_VERSION);
        g.writeNumber(messageType);
        g.writeNumber(seqId);
    }

    private void writeMessageEnd(JsonGenerator g) throws IOException {
        g.writeEndArray();
        g.close();
    }
}
