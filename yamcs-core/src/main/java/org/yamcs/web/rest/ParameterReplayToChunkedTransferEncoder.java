package org.yamcs.web.rest;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.ReplayStatus;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * Reads a yamcs replay and maps it directly to an output buffer. If that buffer grows larger
 * than the treshold size for one chunk, this will cause a chunk to be written out.
 * Could maybe be replaced by using built-in netty functionality, but would need to investigate.
 */
public abstract class ParameterReplayToChunkedTransferEncoder extends RestParameterReplayListener {
    
    private static final Logger log = LoggerFactory.getLogger(ParameterReplayToChunkedTransferEncoder.class);
    private static final int CHUNK_TRESHOLD = 8096;
    
    private ByteBuf buf;
    protected ByteBufOutputStream bufOut;
    
    protected RestRequest req;
    protected String contentType;
    protected boolean failed = false;
    
    public ParameterReplayToChunkedTransferEncoder(RestRequest req, String contentType) throws RestException {
        super();
        this.req = req;
        this.contentType = contentType;
        resetBuffer();
        RestUtils.startChunkedTransfer(req, contentType);
    }
    
    protected void resetBuffer() {
        buf = req.getChannelHandlerContext().alloc().buffer();
        bufOut = new ByteBufOutputStream(buf);
    }
    
    protected void closeBufferOutputStream() throws IOException {
        bufOut.close();
    }
    
    @Override
    public void onParameterData(ParameterData pdata) {
        if (failed) {
            log.warn("Already failed. Ignoring parameter data");
            return;
        }
        try {
            processParameterData(pdata, bufOut);
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                closeBufferOutputStream();
                RestUtils.writeChunk(req, buf);
                resetBuffer();
            }
        } catch (IOException e) {
            log.error("Closing replay due to IO error", e);
            failed = true;
            requestReplayAbortion();
        }
    }
    
    public abstract void processParameterData(ParameterData pdata, ByteBufOutputStream bufOut) throws IOException;
    
    @Override
    public void stateChanged(ReplayStatus rs) {
        if (failed) {
            log.info("Closing channel because transfer failed");
            req.getChannelHandlerContext().channel().close();
            return;
        }
        try {
            closeBufferOutputStream();
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
