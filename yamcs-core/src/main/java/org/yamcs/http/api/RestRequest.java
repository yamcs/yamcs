package org.yamcs.http.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.api.MediaType;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.api.Router.RouteMatch;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;

/**
 * Encapsulates everything to do with one Rest Request. Object is gc-ed, when request ends.
 */
public class RestRequest {

    private ChannelHandlerContext channelHandlerContext;
    private FullHttpRequest httpRequest;
    private User user;
    private RouteMatch routeMatch;

    CompletableFuture<Void> cf = new CompletableFuture<>();
    static AtomicInteger counter = new AtomicInteger();
    final int requestId;
    long txSize = 0;
    int statusCode;

    public RestRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest, User user) {
        this.channelHandlerContext = channelHandlerContext;
        this.httpRequest = httpRequest;
        this.user = user;
        this.requestId = counter.incrementAndGet();
    }

    RouteMatch getRouteMatch() {
        return routeMatch;
    }

    void setRouteMatch(RouteMatch routeMatch) {
        this.routeMatch = routeMatch;
    }

    public boolean hasRouteParam(String name) {
        try {
            return routeMatch.regexMatch.group(name) != null;
        } catch (IllegalArgumentException e) {
            // Could likely be improved, we need this catch in case of multiple @Route annotations
            // for the same method. Because then above call could throw an error if the requested
            // group is not present in one of the patterns
            return false;
        }
    }

    public String getRouteParam(String name) {
        return routeMatch.getRouteParam(name);
    }

    public void reportStatusCode(int statusCode) {
        if (this.statusCode != 0) {
            throw new IllegalArgumentException("Status code already set to " + this.statusCode);
        }
        this.statusCode = statusCode;
    }

    /**
     * 
     * @return unique across running yamcs server rest request id used to aid in tracking the request executin in the
     *         log file
     * 
     */
    public int getRequestId() {
        return requestId;
    }

    public User getUser() {
        return user;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    public FullHttpRequest getHttpRequest() {
        return httpRequest;
    }

    public boolean hasBody() {
        return HttpUtil.getContentLength(httpRequest) > 0;
    }

    /**
     * Deserializes the incoming message extracted from the body. This does not care about what the HTTP method is. Any
     * required checks should be done elsewhere.
     * <p>
     * This method is only able to read JSON or Protobuf, the two auto-supported serialization mechanisms. If a certain
     * operation needs to read anything else, it should check for that itself, and then use
     * {@link #bodyAsInputStream()}.
     */
    public <T extends Message.Builder> T bodyAsMessage(T builder) throws BadRequestException {
        MediaType sourceContentType = deriveSourceContentType();
        // Allow for empty body, otherwise user has to specify '{}'
        if (HttpUtil.getContentLength(httpRequest) > 0) {
            if (MediaType.PROTOBUF.equals(sourceContentType)) {
                try (InputStream cin = bodyAsInputStream()) {
                    builder.mergeFrom(cin);
                } catch (IOException e) {
                    throw new BadRequestException(e);
                }
            } else {
                try {
                    String json = httpRequest.content().toString(StandardCharsets.UTF_8);
                    JsonFormat.parser().merge(json, builder);
                } catch (InvalidProtocolBufferException e) {
                    throw new BadRequestException(e);
                }
            }
        }
        return builder;
    }

    public InputStream bodyAsInputStream() {
        return new ByteBufInputStream(httpRequest.content());
    }

    /**
     * @return see {@link HttpRequestHandler#getContentType(HttpRequest)}
     */
    public MediaType deriveSourceContentType() {
        return HttpRequestHandler.getContentType(httpRequest);
    }

    public MediaType deriveTargetContentType() {
        return deriveTargetContentType(httpRequest);
    }

    /**
     * Derives an applicable content type for the output. This tries to match JSON or BINARY media types with the ACCEPT
     * header, else it will revert to the (derived) source content type.
     *
     * @return the content type that will be used for the response message
     */
    public static MediaType deriveTargetContentType(HttpRequest httpRequest) {
        MediaType mt = MediaType.JSON;
        if (httpRequest.headers().contains(HttpHeaderNames.ACCEPT)) {
            String acceptedContentType = httpRequest.headers().get(HttpHeaderNames.ACCEPT);
            mt = MediaType.from(acceptedContentType);
        } else if (httpRequest.headers().contains(HttpHeaderNames.CONTENT_TYPE)) {
            String declaredContentType = httpRequest.headers().get(HttpHeaderNames.CONTENT_TYPE);
            mt = MediaType.from(declaredContentType);
        }

        // we only support one of these two for the output, so just force JSON by default
        if (mt != MediaType.JSON && mt != MediaType.PROTOBUF) {
            mt = MediaType.JSON;
        }
        return mt;
    }

    /**
     * 
     * When the request is finished, the CompleteableFuture has to be used to signal the end.
     * 
     * 
     * @return future to be used to signal the end of processing the request
     * 
     */
    public CompletableFuture<Void> getCompletableFuture() {
        return cf;
    }

    /**
     * Get the number of bytes transferred as the result of the REST call. It should not include the http headers. Note
     * that the number might be increased before the data is sent so it will be wrong if there was an error sending
     * data.
     * 
     * 
     * @return number of bytes transferred as part of the request
     */
    public long getTransferredSize() {
        return txSize;
    }

    /**
     * add numBytes to the transferred size
     * 
     * @param numBytes
     */
    public void addTransferredSize(long numBytes) {
        txSize += numBytes;
    }

    public static class IntervalResult {
        private long start;
        private long stop;
        private boolean inclusiveStart = true;
        private boolean inclusiveStop = false;

        public IntervalResult(long start, long stop) {
            this.start = start;
            this.stop = stop;
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

        public void setStart(long start, boolean inclusive) {
            this.start = start;
            this.inclusiveStart = inclusive;
        }

        public void setStop(long stop, boolean inclusive) {
            this.stop = stop;
            this.inclusiveStop = inclusive;
        }

        public String asSqlCondition(String col) {
            StringBuilder buf = new StringBuilder();
            if (start != TimeEncoding.INVALID_INSTANT) {
                buf.append(col);
                buf.append(inclusiveStart ? " >= " : " > ");
                buf.append(start);
                if (stop != TimeEncoding.INVALID_INSTANT) {
                    buf.append(" and ").append(col);
                    buf.append(inclusiveStop ? " <= " : " < ");
                    buf.append(stop);
                }
            } else {
                buf.append(col);
                buf.append(inclusiveStop ? " <= " : " < ");
                buf.append(stop);
            }
            return buf.toString();
        }
    }

    /**
     * returns the body of the http request
     * 
     */
    public ByteBuf getRequestContent() {
        return httpRequest.content();
    }
}
