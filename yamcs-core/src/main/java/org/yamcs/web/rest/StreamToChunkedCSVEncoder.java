package org.yamcs.web.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.web.AbstractRequestHandler;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

import com.csvreader.CsvWriter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * Reads a yamcs stream and maps it directly to an output buffer. If that buffer grows larger
 * than the treshold size for one chunk, this will cause a chunk to be written out.
 * Could maybe be replaced by using built-in netty functionality, but would need to investigate.
 */
public abstract class StreamToChunkedCSVEncoder extends RestStreamSubscriber {
    
    private static final Logger log = LoggerFactory.getLogger(StreamToChunkedCSVEncoder.class);
    private static final int CHUNK_TRESHOLD = 8096;
    
    private RestRequest req;
    private ByteBuf buf;
    private BufferedWriter bw;
    private CsvWriter csvWriter;
    
    public StreamToChunkedCSVEncoder(RestRequest req) throws RestException {
        super();
        this.req = req;
        
        RestUtils.startChunkedTransfer(req, AbstractRequestHandler.CSV_MIME_TYPE);
        resetBuffer();

        String[] csvHeader = getCSVHeader();
        if (csvHeader != null) {
            try {
                csvWriter.writeRecord(csvHeader);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
        }
    }
    
    private void resetBuffer() {
        buf = req.getChannelHandlerContext().alloc().buffer();
        bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)));
        csvWriter = new CsvWriter(bw, '\t');
    }

    @Override
    public void onTuple(Tuple tuple) {
        try {
            processTuple(tuple, csvWriter);
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                csvWriter.close();
                RestUtils.writeChunk(req, buf);
                resetBuffer();
            }
        } catch (IOException e) {
            log.error("Skipping chunk", e);
        }
    }
    
    public String[] getCSVHeader() {
        return null;
    }
    
    public abstract void processTuple(Tuple tuple, CsvWriter csvWriter) throws IOException;

    @Override
    public void streamClosed(Stream stream) {
        try {
            csvWriter.close();
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
