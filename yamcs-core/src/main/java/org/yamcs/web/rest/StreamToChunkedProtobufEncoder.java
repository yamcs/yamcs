package org.yamcs.web.rest;

import static org.yamcs.web.AbstractRequestHandler.PROTOBUF_MIME_TYPE;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * Reads a yamcs stream and maps it directly to an output buffer. If that buffer grows larger
 * than the treshold size for one chunk, this will cause a chunk to be written out.
 * Could maybe be replaced by using built-in netty functionality, but would need to investigate.
 */
public abstract class StreamToChunkedProtobufEncoder<T extends MessageLite> extends RestStreamSubscriber {
    
    private static final Logger log = LoggerFactory.getLogger(StreamToChunkedProtobufEncoder.class);
    private static final int CHUNK_TRESHOLD = 8096;
    
    private RestRequest req;
    private Schema<T> schema;
    private String contentType;
    
    private ByteBuf buf;
    private ByteBufOutputStream bufOut;
    
    public StreamToChunkedProtobufEncoder(RestRequest req, Schema<T> schema) throws RestException {
        super();
        this.req = req;
        this.schema = schema;
        contentType = req.deriveTargetContentType();
        resetBuffer();
        RestUtils.startChunkedTransfer(req, contentType);
    }
    
    private void resetBuffer() {
        buf = req.getChannelHandlerContext().alloc().buffer();
        bufOut = new ByteBufOutputStream(buf);
    }

    @Override
    public void onTuple(Tuple tuple) {
        try {
            T msg = mapTuple(tuple);
            bufferMessage(msg);
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                bufOut.close();
                RestUtils.writeChunk(req, buf);
                resetBuffer();
            }
        } catch (IOException e) {
            log.error("Skipping chunk", e);
        }
    }
    
    private void bufferMessage(T msg) throws IOException {
        if (PROTOBUF_MIME_TYPE.equals(contentType)) {
            msg.writeDelimitedTo(bufOut);
        } else {
            JsonGenerator generator = req.createJsonGenerator(bufOut);
            JsonIOUtil.writeTo(generator, msg, schema, false);
            generator.close();
        }
    }
    
    public abstract T mapTuple(Tuple tuple);

    @Override
    public void streamClosed(Stream stream) {
        try {
            bufOut.close();
            if (buf.readableBytes() > 0) {
                RestUtils.writeChunk(req, buf).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        RestUtils.stopChunkedTransfer(req);
                    }
                });
            } else {
                RestUtils.stopChunkedTransfer(req);
            }
        } catch (IOException e) {
            log.error("Could not write final chunk of data", e);
        }
    }
}
