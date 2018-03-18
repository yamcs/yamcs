package org.yamcs.web.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.yamcs.api.MediaType;
import org.yamcs.web.HttpException;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBufOutputStream;

/**
 * Facilitates sending protobuf messages with chunked transfer encoding
 */
public abstract class StreamToChunkedProtobufEncoder<T extends Message> extends StreamToChunkedTransferEncoder {

    public StreamToChunkedProtobufEncoder(RestRequest req) throws HttpException {
        this(req, null);
    }

    public StreamToChunkedProtobufEncoder(RestRequest req, String filename) throws HttpException {
        super(req, req.deriveTargetContentType(), filename);
    }

    @Override
    public void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException {
        T msg = mapTuple(tuple);
        if (MediaType.PROTOBUF.equals(contentType)) {
            msg.writeDelimitedTo(bufOut);
        } else {
            String json = JsonFormat.printer().print(msg);
            bufOut.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    public abstract T mapTuple(Tuple tuple);
}
