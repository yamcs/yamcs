package org.yamcs.web.rest;

import static org.yamcs.web.AbstractRequestHandler.BINARY_MIME_TYPE;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * Reads a yamcs stream and maps it directly to an output buffer. If that buffer grows larger
 * than the treshold size for one chunk, this will cause a chunk to be written out.
 * Could maybe be replaced by using built-in netty functionality, but would need to investigate.
 */
public abstract class StreamToChunkedBinaryEncoder extends RestStreamSubscriber {
    
    private static final Logger log = LoggerFactory.getLogger(StreamToChunkedBinaryEncoder.class);
    private static final int CHUNK_TRESHOLD = 8096;
    
    private RestRequest req;
    
    private ByteBuf buf;
    private ByteBufOutputStream bufOut;
    
    public StreamToChunkedBinaryEncoder(RestRequest req) throws RestException {
        super();
        this.req = req;
        resetBuffer();
        RestUtils.startChunkedTransfer(req, BINARY_MIME_TYPE);
    }
    
    private void resetBuffer() {
        buf = req.getChannelHandlerContext().alloc().buffer();
        bufOut = new ByteBufOutputStream(buf);
    }

    @Override
    public void onTuple(Tuple tuple) {
        try {
            processTuple(tuple, bufOut);
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                bufOut.close();
                RestUtils.writeChunk(req, buf);
                resetBuffer();
            }
        } catch (IOException e) {
            log.error("Skipping chunk", e);
        }
    }
    
    public abstract void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException;

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
