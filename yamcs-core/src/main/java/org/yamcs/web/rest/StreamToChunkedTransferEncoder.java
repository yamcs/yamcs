package org.yamcs.web.rest;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.HttpRequestHandler.ChunkedTransferStats;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;

/**
 * Reads a yamcs stream and maps it directly to an output buffer. If that buffer grows larger
 * than the treshold size for one chunk, this will cause a chunk to be written out.
 * Could maybe be replaced by using built-in netty functionality, but would need to investigate.
 */
public abstract class StreamToChunkedTransferEncoder extends RestStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(StreamToChunkedTransferEncoder.class);
    private static final int CHUNK_TRESHOLD = 8096;

    private RestRequest req;

    private ByteBuf buf;
    protected ByteBufOutputStream bufOut;

    protected MediaType contentType;
    protected boolean failed = false;
    private ChunkedTransferStats stats;

    public StreamToChunkedTransferEncoder(RestRequest req, MediaType contentType) throws HttpException {
        super();
        this.req = req;
        this.contentType = contentType;
        resetBuffer();
        HttpRequestHandler.startChunkedTransfer(req.getChannelHandlerContext(), req.getHttpRequest(), contentType, null);
        stats = req.getChannelHandlerContext().attr(HttpRequestHandler.CTX_CHUNK_STATS).get();
    }

    protected void resetBuffer() {
        buf = req.getChannelHandlerContext().alloc().buffer();
        bufOut = new ByteBufOutputStream(buf);
    }

    protected void closeBufferOutputStream() throws IOException {
        bufOut.close();
    }

    @Override
    public void processTuple(Stream stream, Tuple tuple) {
        if (failed) {
            log.warn("R{}: Already failed. Ignoring tuple", req.getRequestId());
            return;
        }
        try {
            processTuple(tuple, bufOut);
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                closeBufferOutputStream();
                writeChunk();
                resetBuffer();
            }
        } catch (ClosedChannelException e) {
            log.info("R{}: Closing stream due to client closing connection", req.getRequestId());
            failed = true;
            stream.close();
            RestHandler.abortRequest(req);
        } catch (IOException e) {
            log.error("R{}: Closing stream due to IO error", req.getRequestId(), e);
            failed = true;
            stream.close();
            RestHandler.completeWithError(req, new InternalServerErrorException(e));
        } 
    }

    public abstract void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException;

    @Override
    public void streamClosed(Stream stream) {
        if (failed) {
            Channel chan = req.getChannelHandlerContext().channel();
            if(chan.isOpen()) {
                log.warn("R{}: Closing channel because transfer failed", req.getRequestId());
                req.getChannelHandlerContext().channel().close();
            }
            return;
        }
        try {
            closeBufferOutputStream();
            if (buf.readableBytes() > 0) {
                writeChunk();
            }
            RestHandler.completeChunkedTransfer(req);
        } catch (IOException e) {
            log.error("R{}: Could not write final chunk of data", req.getRequestId(), e);
            RestHandler.completeWithError(req, new InternalServerErrorException(e));
        }
    }

    private void writeChunk() throws IOException {
        int txSize = buf.readableBytes();
        req.addTransferredSize(txSize);
        stats.totalBytes += txSize;
        stats.chunkCount++;
        HttpRequestHandler.writeChunk(req.getChannelHandlerContext(), buf);
    }
}
