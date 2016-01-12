package org.yamcs.web.rest;

import java.io.IOException;

import org.yamcs.api.MediaType;
import org.yamcs.web.HttpException;
import org.yamcs.yarch.Tuple;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBufOutputStream;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * Facilitates sending protobuf messages with chunked transfer encoding
 */
public abstract class StreamToChunkedProtobufEncoder<T extends MessageLite> extends StreamToChunkedTransferEncoder {
    
    private RestRequest req;
    private Schema<T> schema;
    
    public StreamToChunkedProtobufEncoder(RestRequest req, Schema<T> schema) throws HttpException {
        super(req, req.deriveTargetContentType());
        this.req = req;
        this.schema = schema;
    }
    
    @Override
    public void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException {
        T msg = mapTuple(tuple);
        if (MediaType.PROTOBUF.equals(contentType)) {
            msg.writeDelimitedTo(bufOut);
        } else {
            JsonGenerator generator = req.createJsonGenerator(bufOut);
            JsonIOUtil.writeTo(generator, msg, schema, false);
            generator.close();
        }
    }
    
    public abstract T mapTuple(Tuple tuple);
}
