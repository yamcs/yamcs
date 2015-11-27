package org.yamcs.web.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.web.AbstractRequestHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * Reads a yamcs stream and maps it directly to an output buffer. If that buffer grows larger
 * than the treshold size for one chunk, this will cause a chunk to be written out.
 * Could maybe be replaced by using built-in netty functionality, but would need to investigate.
 */
public class ReplayToChunkedParameterCSV extends RestParameterReplayListener {
    
    private static final Logger log = LoggerFactory.getLogger(ReplayToChunkedParameterCSV.class);
    private static final int CHUNK_TRESHOLD = 8096;
    
    private RestRequest req;
    private List<NamedObjectId> idList;
    private ByteBuf buf;
    private BufferedWriter bw;
    private ParameterFormatter formatter;
    
    public ReplayToChunkedParameterCSV(RestRequest req, List<NamedObjectId> idList) throws RestException {
        super();
        this.req = req;
        this.idList = idList;
        RestUtils.startChunkedTransfer(req, AbstractRequestHandler.CSV_MIME_TYPE);
        resetBuffer(true);
    }
    
    private void resetBuffer(boolean writeHeader) {
        buf = req.getChannelHandlerContext().alloc().buffer();
        bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)));
        formatter = new ParameterFormatter(bw, idList, '\t');
        formatter.setWriteHeader(writeHeader);
    }
    
    @Override
    public void onParameterData(ParameterData pdata) {
        try {
            formatter.writeParameters(pdata.getParameterList());
            //formatter.flush(); // Hmm would prefer not to flush everytime
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                formatter.close();
                RestUtils.writeChunk(req, buf);
                resetBuffer(false);
            }
        } catch (IOException e) {
            log.error("Skipping chunk", e);
        }
    }
    
    @Override
    public void stateChanged(ReplayStatus rs) {
        try {
            formatter.close();
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
