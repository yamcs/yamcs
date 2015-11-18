package org.yamcs.web.rest;

import static org.yamcs.web.AbstractRequestHandler.BINARY_MIME_TYPE;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.web.AbstractRequestHandler;

import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.protostuff.JsonIOUtil;

/**
 * Reads a yamcs stream and maps it directly to an output buffer. If that buffer grows larger
 * than the treshold size for one chunk, this will cause a chunk to be written out.
 * Could maybe be replaced by using built-in netty functionality, but would need to investigate.
 */
public class ReplayToChunkedParameterProtobuf extends RestParameterReplayListener {
    
    private static final Logger log = LoggerFactory.getLogger(ReplayToChunkedParameterProtobuf.class);
    private static final int CHUNK_TRESHOLD = 8096;
    
    private RestRequest req;
    private String contentType;
    
    private ByteBuf buf;
    private ByteBufOutputStream bufOut;
    
    public ReplayToChunkedParameterProtobuf(RestRequest req) throws RestException {
        this.req = req;
        contentType = req.deriveTargetContentType();
        resetBuffer();
        RestUtils.startChunkedTransfer(req, AbstractRequestHandler.CSV_MIME_TYPE);
    }
    
    private void resetBuffer() {
        buf = req.getChannelHandlerContext().alloc().buffer();
        bufOut = new ByteBufOutputStream(buf);
    }
    
    @Override
    public void onParameterData(ParameterData pdata) {
        try {
            for (ParameterValue pval : pdata.getParameterList()) {
                bufferMessage(pval);
            }
            //bufOut.flush(); // hmm
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                bufOut.close();
                RestUtils.writeChunk(req, buf);
                resetBuffer();
            }
        } catch (IOException e) {
            log.error("Skipping chunk", e);
        }
    }
    
    private void bufferMessage(ParameterValue msg) throws IOException {
        if (BINARY_MIME_TYPE.equals(contentType)) {
            msg.writeDelimitedTo(bufOut);
        } else {
            JsonGenerator generator = req.createJsonGenerator(bufOut);
            JsonIOUtil.writeTo(generator, msg, SchemaPvalue.ParameterValue.WRITE, false);
            generator.close();
        }
    }
    
    @Override
    public void stateChanged(ReplayStatus rs) {
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
