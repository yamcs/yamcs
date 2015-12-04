package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.utils.TimeEncoding;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * These methods are looking for a better home. A ResponseBuilder ?
 */
public class RestUtils {
    
    private static final Logger log = LoggerFactory.getLogger(RestUtils.class);
    
    public static void sendResponse(RestResponse restResponse) throws RestException {
        if (restResponse == null) return; // Allowed, when the specific handler prefers to do this
        HttpResponse httpResponse;
        if (restResponse.getBody() == null) {
            httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
            setContentLength(httpResponse, 0);
        } else {
            httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, restResponse.getBody());
            httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, restResponse.getContentType());
            setContentLength(httpResponse, restResponse.getBody().readableBytes());
        }

        RestRequest restRequest = restResponse.getRestRequest();
        ChannelFuture writeFuture = restRequest.getChannelHandlerContext().writeAndFlush(httpResponse);

        // Decide whether to close the connection or not.
        if (!isKeepAlive(restRequest.getHttpRequest())) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    /**
     * Sends base HTTP response indicating that we'll use chunked transfer encoding
     */
    public static ChannelFuture startChunkedTransfer(RestRequest req, String contentType) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
        response.headers().set(Names.CONTENT_TYPE, contentType);
        
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        ChannelFuture writeFuture = ctx.writeAndFlush(response);
        writeFuture.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        return writeFuture;
    }
    
    public static ChannelFuture writeChunk(RestRequest req, ByteBuf buf) throws IOException {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        Channel ch = ctx.channel();
        log.debug("Writing a buf");
        if (!ch.isOpen()) {
            throw new IOException("Channel not or no longer open");
        }
        ChannelFuture writeFuture = ctx.writeAndFlush(new DefaultHttpContent(buf));
        try {
            if (!ch.isWritable()) {
                log.warn("Channel open, but not writable. Waiting it out for max 10 seconds.");
                boolean writeCompleted = writeFuture.await(10, TimeUnit.SECONDS);
                if (!writeCompleted) {
                    throw new IOException("Channel did not become writable in 10 seconds");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for channel to become writable", e);
            throw new IOException(e);
        }
        return writeFuture;
    }
    
    /**
     * Send empty chunk downstream to signal end of response
     */
    public static void stopChunkedTransfer(RestRequest req) {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        ChannelFuture chunkWriteFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        chunkWriteFuture.addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * Returns true if the request specifies descending by use of the query string paramter 'order=desc'
     */
    public static boolean asksDescending(RestRequest req, boolean descendByDefault) throws RestException {
        if (req.hasQueryParameter("order")) {
            switch (req.getQueryParameter("order").toLowerCase()) {
            case "asc":
            case "ascending":
                return false;
            case "desc":
            case "descending":
                return true;
            default:
                throw new BadRequestException("Unsupported value for order parameter. Expected 'asc' or 'desc'");
            }            
        } else {
            return descendByDefault;
        }
    }
    
    /**
     * Interprets the provided string as either an instant, or an ISO 8601
     * string and returns it as an instant of type long
     */
    public static long parseTime(String datetime) {
        try {
            return Long.parseLong(datetime);
        } catch (NumberFormatException e) {
            return TimeEncoding.parse(datetime);
        }
    }
    
    public static IntervalResult scanForInterval(RestRequest req) throws RestException {
        return new IntervalResult(req);
    }
    
    public static class IntervalResult {
        private final long start;
        private final long stop;
        
        IntervalResult(RestRequest req) throws BadRequestException {
            start = req.getQueryParameterAsDate("start", TimeEncoding.INVALID_INSTANT);
            stop = req.getQueryParameterAsDate("stop", TimeEncoding.INVALID_INSTANT);
        }
        
        public boolean hasInterval() {
            return start != TimeEncoding.INVALID_INSTANT || stop != TimeEncoding.INVALID_INSTANT;
        }
        
        public boolean hasStart() {
            return start != TimeEncoding.INVALID_INSTANT;
        }
        
        public boolean hasStop() {
            return stop != TimeEncoding.INVALID_INSTANT;
        }
        
        public long getStart() {
            return start;
        }
        
        public long getStop() {
            return stop;
        }
        
        public TimeInterval asTimeInterval() {
            TimeInterval intv = new TimeInterval();
            if (hasStart()) intv.setStart(start);
            if (hasStop()) intv.setStop(stop);
            return intv;
        }
        
        public String asSqlCondition(String col) {
            StringBuilder buf = new StringBuilder();
            if (start != TimeEncoding.INVALID_INSTANT) {
                buf.append(col).append(" >= ").append(start);
                if (stop != TimeEncoding.INVALID_INSTANT) {
                    buf.append(" and ").append(col).append(" < ").append(stop);
                }
            } else {
                buf.append(col).append(" < ").append(stop);
            }
            return buf.toString();
        }
    }
}
