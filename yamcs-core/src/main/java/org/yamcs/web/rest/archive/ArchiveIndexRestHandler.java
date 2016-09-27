package org.yamcs.web.rest.archive;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexServer;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.HttpRequestHandler.ChunkedTransferStats;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.Route;

import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.LastHttpContent;
import io.protostuff.JsonIOUtil;

/**
 * Serves archive indexes through a web api.
 *
 * <p>These responses use chunked encoding with an unspecified content length, which enables
 * us to send large dumps without needing to determine a content length on the server.
 */
public class ArchiveIndexRestHandler extends RestHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveIndexRestHandler.class);

    /**
     * indexes a combination of multiple indexes. If nothing is specified, sends all available
     */
    @Route(path = "/api/archive/:instance/indexes", method = "GET")
    public void downloadIndexes(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        IntervalResult ir = req.scanForInterval();
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

    @Route(path = "/api/archive/:instance/indexes/packets", method = "GET")
    public void downloadPacketIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        IntervalResult ir = req.scanForInterval();
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

    @Route(path = "/api/archive/:instance/indexes/pp", method = "GET")
    public void downloadPpIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        IntervalResult ir = req.scanForInterval();
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

    @Route(path = "/api/archive/:instance/indexes/commands", method = "GET")
    public void downloadCommandHistoryIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        IntervalResult ir = req.scanForInterval();
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

    @Route(path = "/api/archive/:instance/indexes/events", method = "GET")
    public void downloadEventIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        IntervalResult ir = req.scanForInterval();
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

    @Route(path = "/api/archive/:instance/indexes/completeness", method = "GET")
    public void downloadCompletenessIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        IntervalResult ir = req.scanForInterval();
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
        private final MediaType contentType;
        private final boolean unpack;

        private ByteBuf buf;
        private ByteBufOutputStream bufOut;
        private ChannelFuture lastChannelFuture;
        private ChunkedTransferStats stats;

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
                lastChannelFuture = HttpRequestHandler.startChunkedTransfer(req.getChannelHandlerContext(), req.getHttpRequest(), contentType, null);
                stats = req.getChannelHandlerContext().attr(HttpRequestHandler.CTX_CHUNK_STATS).get();
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
                writeChunk();
                resetBuffer();
            }
        }

        private void bufferArchiveRecord(ArchiveRecord msg) throws IOException {
            if (MediaType.PROTOBUF.equals(contentType)) {
                msg.writeDelimitedTo(bufOut);
            } else {
                JsonGenerator generator = req.createJsonGenerator(bufOut);
                JsonIOUtil.writeTo(generator, msg, SchemaYamcs.ArchiveRecord.WRITE, false);
                generator.close();
            }
        }

        private void bufferIndexResult(IndexResult msg) throws IOException {
            if (MediaType.PROTOBUF.equals(contentType)) {
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
                    writeChunk();
                }
                req.getChannelHandlerContext().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener(ChannelFutureListener.CLOSE)
                .addListener(l-> req.getCompletableFuture().complete(null));
            } catch (IOException e) {
                log.error("Could not write final chunk of data", e);
                req.getChannelHandlerContext().close();
            }
        }

        private void writeChunk() throws IOException {
            int txSize = buf.readableBytes();
            req.addTransferredSize(txSize);
            stats.totalBytes += buf.readableBytes();
            stats.chunkCount++;
            lastChannelFuture = HttpRequestHandler.writeChunk(req.getChannelHandlerContext(), buf);
        }
    }

    private IndexServer verifyIndexServer(RestRequest req, String instance) throws HttpException {
        verifyInstance(req, instance);
        IndexServer indexServer = YamcsServer.getService(instance, IndexServer.class);
        if (indexServer == null) {
            throw new BadRequestException("Index service not enabled for instance '" + instance + "'");
        } else {
            return indexServer;
        }
    }
}
