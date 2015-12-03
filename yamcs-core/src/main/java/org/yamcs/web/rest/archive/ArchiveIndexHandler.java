package org.yamcs.web.rest.archive;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexServer;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.RestUtils;
import org.yamcs.web.rest.RestUtils.IntervalResult;

import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.protostuff.JsonIOUtil;

/** 
 * Serves archive indexes through a web api.
 *
 * <p>These responses use chunked encoding with an unspecified content length, which enables
 * us to send large dumps without needing to determine a content length on the server.
 */
public class ArchiveIndexHandler extends RestRequestHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ArchiveIndexHandler.class);
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        IndexServer indexServer = YamcsServer.getService(instance, IndexServer.class);
        if (indexServer == null) {
            throw new BadRequestException("Index service not enabled for instance '" + instance + "'");
        }
        
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            downloadIndexes(req, indexServer);
            return null;
        } else {
            switch (req.getPathSegment(pathOffset)) {
            case "packets":
                req.assertGET();
                downloadPacketIndex(req, indexServer);
                return null;
            case "pp":
                req.assertGET();
                downloadPpIndex(req, indexServer);
                return null;
            case "events":
                req.assertGET();
                downloadEventIndex(req, indexServer);
                return null;
            case "commands":
                req.assertGET();
                downloadCommandHistoryIndex(req, indexServer);
                return null;
            case "completeness":
                req.assertGET();
                downloadCompletenessIndex(req, indexServer);
                return null;
            default:
                throw new NotFoundException(req);
            }
        }
    }
    
    /**
     * Downloads a combination of multiple indexes. If nothing is specified, returns empty
     */
    private void downloadIndexes(RestRequest req, IndexServer indexServer) throws RestException {
        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(indexServer.getInstance());
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }

        if (req.hasQueryParameter("packetname")) {
            for (String names : req.getQueryParameterList("packetname")) {
                for (String name : names.split(",")) {
                    requestb.addTmPacket(NamedObjectId.newBuilder().setName(name.trim()));
                }
            }
        }
        
        Set<String> filter = new HashSet<>();
        if (req.hasQueryParameter("filter")) {
            for (String names : req.getQueryParameterList("filter")) {
                for (String name : names.split(",")) {
                    filter.add(name.toLowerCase().trim());
                }
            }
        }
        
        if (filter.isEmpty() && requestb.getTmPacketCount() == 0) {
            requestb.setSendAllTm(true);
            requestb.setSendAllPp(true);
            requestb.setSendAllCmd(true);
            requestb.setSendAllEvent(true);
            requestb.setSendCompletenessIndex(true);
        } else {
            requestb.setSendAllTm(filter.contains("tm") && requestb.getTmPacketCount() == 0);
            requestb.setSendAllPp(filter.contains("pp"));
            requestb.setSendAllCmd(filter.contains("commands"));
            requestb.setSendAllEvent(filter.contains("events"));
            requestb.setSendCompletenessIndex(filter.contains("completeness"));
        }
        
        try {
            indexServer.submitIndexRequest(requestb.build(), new ChunkedIndexResultProtobufEncoder(req, false));
        } catch (YamcsException e) {
            log.error("Error while processing index request", e);
        }
    }
    
    private void downloadPacketIndex(RestRequest req, IndexServer indexServer) throws RestException {
        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(indexServer.getInstance());
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }

        if (req.hasQueryParameter("name")) {
            for (String names : req.getQueryParameterList("name")) {
                for (String name : names.split(",")) {
                    requestb.addTmPacket(NamedObjectId.newBuilder().setName(name.trim()));
                }
            }
        }
        if (requestb.getTmPacketCount() == 0) {
            requestb.setSendAllTm(true);
        }
        
        try {
            indexServer.submitIndexRequest(requestb.build(), new ChunkedIndexResultProtobufEncoder(req, true));
        } catch (YamcsException e) {
            log.error("Error while processing index request", e);
        }
    }
    
    private void downloadPpIndex(RestRequest req, IndexServer indexServer) throws RestException {
        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(indexServer.getInstance());
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        requestb.setSendAllPp(true);
        
        try {
            indexServer.submitIndexRequest(requestb.build(), new ChunkedIndexResultProtobufEncoder(req, true));
        } catch (YamcsException e) {
            log.error("Error while processing index request", e);
        }
    }
    
    private void downloadCommandHistoryIndex(RestRequest req, IndexServer indexServer) throws RestException {
        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(indexServer.getInstance());
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        requestb.setSendAllCmd(true);
        
        try {
            indexServer.submitIndexRequest(requestb.build(), new ChunkedIndexResultProtobufEncoder(req, true));
        } catch (YamcsException e) {
            log.error("Error while processing index request", e);
        }
    }
    
    private void downloadEventIndex(RestRequest req, IndexServer indexServer) throws RestException {
        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(indexServer.getInstance());
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        requestb.setSendAllEvent(true);
        
        try {
            indexServer.submitIndexRequest(requestb.build(), new ChunkedIndexResultProtobufEncoder(req, true));
        } catch (YamcsException e) {
            log.error("Error while processing index request", e);
        }
    }
    
    private void downloadCompletenessIndex(RestRequest req, IndexServer indexServer) throws RestException {
        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(indexServer.getInstance());
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        requestb.setSendCompletenessIndex(true);
        
        try {
            indexServer.submitIndexRequest(requestb.build(), new ChunkedIndexResultProtobufEncoder(req, true));
        } catch (YamcsException e) {
            log.error("Error while processing index request", e);
        }
    }
    
    private static class ChunkedIndexResultProtobufEncoder implements IndexRequestListener {
        
        private static final Logger log = LoggerFactory.getLogger(ChunkedIndexResultProtobufEncoder.class);
        private static final int CHUNK_TRESHOLD = 8096;
        
        private final RestRequest req;
        private final String contentType;
        private final boolean unpack;
        
        private ByteBuf buf;
        private ByteBufOutputStream bufOut;
        
        private boolean first;
        
        // If unpack, the result will be a stream of Archive Records, otherwise IndexResult
        public ChunkedIndexResultProtobufEncoder(RestRequest req, boolean unpack) {
            this.req = req;
            this.unpack = unpack;
            contentType = req.deriveTargetContentType();
            resetBuffer();
            first = true;
        }
        
        private void resetBuffer() {
            buf = req.getChannelHandlerContext().alloc().buffer();
            bufOut = new ByteBufOutputStream(buf);
        }

        @Override
        public void processData(IndexResult indexResult) throws Exception {
            if (first) {
                RestUtils.startChunkedTransfer(req, contentType);
                first = false;
            }
            if (unpack) {
                for (ArchiveRecord rec : indexResult.getRecordsList()) {
                    bufferArchiveRecord(rec);
                }
            } else {
                bufferIndexResult(indexResult);
            }
            if (buf.readableBytes() >= CHUNK_TRESHOLD) {
                bufOut.close();
                RestUtils.writeChunk(req, buf);
                resetBuffer();
            }
        }
        
        private void bufferArchiveRecord(ArchiveRecord msg) throws IOException {
            if (PROTOBUF_MIME_TYPE.equals(contentType)) {
                msg.writeDelimitedTo(bufOut);
            } else {
                JsonGenerator generator = req.createJsonGenerator(bufOut);
                JsonIOUtil.writeTo(generator, msg, SchemaYamcs.ArchiveRecord.WRITE, false);
                generator.close();
            }
        }
        
        private void bufferIndexResult(IndexResult msg) throws IOException {
            if (PROTOBUF_MIME_TYPE.equals(contentType)) {
                msg.writeDelimitedTo(bufOut);
            } else {
                JsonGenerator generator = req.createJsonGenerator(bufOut);
                JsonIOUtil.writeTo(generator, msg, SchemaYamcs.IndexResult.WRITE, false);
                generator.close();
            }
        }

        @Override
        public void finished(boolean success) {
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
}
