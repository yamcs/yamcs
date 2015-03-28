package org.yamcs.web.websocket;

import com.dyuproject.protostuff.JsonIOUtil;
import com.dyuproject.protostuff.Schema;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

import java.io.IOException;
import java.io.StringWriter;

public class JsonEncoder {

    private JsonFactory jsonFactory = new JsonFactory();

    String encodeException(int requestId, String message) throws WebSocketException {
        try {
            StringWriter sw = new StringWriter();
            JsonGenerator g = jsonFactory.createJsonGenerator(sw);
            writeMessageStart(g, WSConstants.MESSAGE_TYPE_EXCEPTION, requestId);
            g.writeStartObject();
            g.writeStringField("et", "STRING");
            g.writeStringField("msg", message);
            g.writeEndObject();
            writeMessageEnd(g);
            return sw.toString();
        } catch (IOException e) {
            throw new WebSocketException(requestId, "Could not encode exception", e);
        }
    }

    <T> String encodeException(int requestId, String exceptionType, T message, Schema<T> targetSchema) throws WebSocketException {
        try {
            StringWriter sw=new StringWriter();
            JsonGenerator g=jsonFactory.createJsonGenerator(sw);
            writeMessageStart(g, WSConstants.MESSAGE_TYPE_EXCEPTION, requestId);
            g.writeStartObject();
            g.writeStringField("et", exceptionType);
            g.writeFieldName("msg");
            JsonIOUtil.writeTo(g, message, targetSchema, false);
            g.writeEndObject();
            writeMessageEnd(g);
            return sw.toString();
        } catch (IOException e) {
            throw new WebSocketException(requestId, "Could not encode exception", e);
        }
    }

    String encodeAckReply(int requestId) throws WebSocketException {
        try {
            StringWriter sw=new StringWriter();
            JsonGenerator g=jsonFactory.createJsonGenerator(sw);
            writeMessageStart(g, WSConstants.MESSAGE_TYPE_REPLY, requestId);
            writeMessageEnd(g);
            return sw.toString();
        } catch (IOException e) {
            throw new WebSocketException(requestId, "Could not encode ACK reply", e);
        }
    }

    <T> String encodeData(int sequenceNumber, ProtoDataType dataType, T message, Schema<T> targetSchema) throws IOException {
        StringWriter sw=new StringWriter();
        JsonGenerator g=jsonFactory.createJsonGenerator(sw);
        writeMessageStart(g, WSConstants.MESSAGE_TYPE_DATA, sequenceNumber);
        g.writeStartObject();
        g.writeStringField("dt", dataType.name());
        g.writeFieldName("data");
        JsonIOUtil.writeTo(g, message, targetSchema, false);
        g.writeEndObject();
        writeMessageEnd(g);
        return sw.toString();
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
