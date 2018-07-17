package org.yamcs.web.rest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.api.MediaType;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.Router.RouteMatch;

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
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AsciiString;

/**
 * Encapsulates everything to do with one Rest Request. Object is gc-ed, when request ends.
 */
public class RestRequest {

    private ChannelHandlerContext channelHandlerContext;
    private FullHttpRequest httpRequest;
    private QueryStringDecoder qsDecoder;
    private User user;
    private RouteMatch routeMatch;

    CompletableFuture<Void> cf = new CompletableFuture<>();
    static AtomicInteger counter = new AtomicInteger();
    final int requestId;
    long txSize = 0;

    public RestRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest,
            QueryStringDecoder qsDecoder, User user) {
        this.channelHandlerContext = channelHandlerContext;
        this.httpRequest = httpRequest;
        this.user = user;
        this.qsDecoder = qsDecoder;
        this.requestId = counter.incrementAndGet();
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

    /**
     * 
     * @return unique across running yamcs server rest request id used to aid in tracking the request executin in the
     *         log file
     * 
     */
    public int getRequestId() {
        return requestId;
    }

    public long getLongRouteParam(String name) throws BadRequestException {
        String routeParam = routeMatch.regexMatch.group(name);
        try {
            return Long.parseLong(routeParam);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Path segment ':" + name + "' is not a valid integer value");
        }
    }

    public int getIntegerRouteParam(String name) throws BadRequestException {
        String routeParam = routeMatch.regexMatch.group(name);
        try {
            return Integer.parseInt(routeParam);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Path segment ':" + name + "' is not a valid integer value");
        }
    }

    public long getDateRouteParam(String name) throws BadRequestException {
        String routeParam = routeMatch.regexMatch.group(name);
        try {
            return Long.parseLong(routeParam);
        } catch (NumberFormatException e) {
            try {
                return TimeEncoding.parse(routeParam);
            } catch (IllegalArgumentException e2) {
                throw new BadRequestException("Path segment ':" + name + "' is not a valid ISO 8601 date string");
            }
        }
    }

    public String getFullPathWithoutQueryString() {
        return qsDecoder.path();
    }

    public boolean hasHeader(String name) {
        return httpRequest.headers().contains(name);
    }

    public String getHeader(AsciiString name) {
        return httpRequest.headers().get(name);
    }

    /**
     * Matches the content type on either the Accept header or a 'format' query param. Should probably better be
     * integrated with the deriveTargetContentType setting.
     */
    public boolean asksFor(MediaType mediaType) {
        if (hasQueryParameter("format")) {
            switch (getQueryParameter("format").toLowerCase()) {
            case "json":
                return MediaType.JSON.equals(mediaType);
            case "csv":
                return MediaType.CSV.equals(mediaType);
            case "proto":
                return MediaType.PROTOBUF.equals(mediaType);
            case "raw":
            case "binary":
                return MediaType.OCTET_STREAM.equals(mediaType);
            default:
                return mediaType.is(getQueryParameter("format"));
            }
        } else {
            return getHttpRequest().headers().contains(HttpHeaderNames.ACCEPT)
                    && mediaType.is(getHttpRequest().headers().get(HttpHeaderNames.ACCEPT));
        }
    }

    public User getUser() {
        return user;
    }

    public boolean hasQueryParameter(String name) {
        return qsDecoder.parameters().containsKey(name);
    }

    public Map<String, List<String>> getQueryParameters() {
        return qsDecoder.parameters();
    }

    public List<String> getQueryParameterList(String name) {
        return qsDecoder.parameters().get(name);
    }

    public List<String> getQueryParameterList(String name, List<String> defaultList) {
        if (hasQueryParameter(name)) {
            return getQueryParameterList(name);
        } else {
            return defaultList;
        }
    }

    public String getQueryParameter(String name) {
        List<String> param = qsDecoder.parameters().get(name);

        if (param == null || param.isEmpty()) {
            return null;
        }
        return param.get(0);
    }

    public String getQueryParameter(String name, String defaultValue) throws BadRequestException {
        if (hasQueryParameter(name)) {
            return getQueryParameter(name);
        } else {
            return defaultValue;
        }
    }

    public int getQueryParameterAsInt(String name) throws BadRequestException {
        String param = getQueryParameter(name);
        try {
            return Integer.parseInt(param);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Query parameter '" + name + "' does not have a valid integer value");
        }
    }

    public int getQueryParameterAsInt(String name, int defaultValue) throws BadRequestException {
        if (hasQueryParameter(name)) {
            return getQueryParameterAsInt(name);
        } else {
            return defaultValue;
        }
    }

    public long getQueryParameterAsLong(String name) throws BadRequestException {
        String param = getQueryParameter(name);
        try {
            return Long.parseLong(param);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Query parameter '" + name + "' does not have a valid integer value");
        }
    }

    public long getQueryParameterAsLong(String name, long defaultValue) throws BadRequestException {
        if (hasQueryParameter(name)) {
            return getQueryParameterAsLong(name);
        } else {
            return defaultValue;
        }
    }

    public long getQueryParameterAsDate(String name) throws BadRequestException {
        String param = getQueryParameter(name);
        try {
            return Long.parseLong(param);
        } catch (NumberFormatException e) {
            return TimeEncoding.parse(param);
        }
    }

    public long getQueryParameterAsDate(String name, long defaultValue) throws BadRequestException {
        if (hasQueryParameter(name)) {
            return getQueryParameterAsDate(name);
        } else {
            return defaultValue;
        }
    }

    public boolean getQueryParameterAsBoolean(String name) {
        List<String> paramList = getQueryParameterList(name);
        String param = paramList.get(0);
        return (param == null || "".equals(param) || "true".equalsIgnoreCase(param)
                || "yes".equalsIgnoreCase(param));
    }

    public boolean getQueryParameterAsBoolean(String name, boolean defaultValue) {
        if (hasQueryParameter(name)) {
            return getQueryParameterAsBoolean(name);
        } else {
            return defaultValue;
        }
    }

    public boolean isSSL() {
        return channelHandlerContext.pipeline().get(SslHandler.class) != null;
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

    public ByteBuf bodyAsBuf() {
        return httpRequest.content();
    }

    /**
     * @return see {@link MediaType#getContentType(HttpRequest)}
     */
    public MediaType deriveSourceContentType() {
        return MediaType.getContentType(httpRequest);
    }

    /**
     * Derives an applicable content type for the output. This tries to match JSON or BINARY media types with the ACCEPT
     * header, else it will revert to the (derived) source content type.
     *
     * @return the content type that will be used for the response message
     */
    public MediaType deriveTargetContentType() {
        return deriveTargetContentType(httpRequest);
    }

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

    public String getBaseURL() {
        String scheme = isSSL() ? "https://" : "http://";
        String host = getHeader(HttpHeaderNames.HOST);
        return (host != null) ? scheme + host : "";
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

    /**
     * Returns true if the request specifies descending by use of the query string paramter 'order=desc'
     */
    public boolean asksDescending(boolean descendByDefault) throws HttpException {
        if (hasQueryParameter("order")) {
            switch (getQueryParameter("order").toLowerCase()) {
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
     * Interprets the provided string as either an instant, or an ISO 8601 string and returns it as an instant of type
     * long
     */
    public static long parseTime(String datetime) {
        try {
            return Long.parseLong(datetime);
        } catch (NumberFormatException e) {
            return TimeEncoding.parse(datetime);
        }
    }

    public IntervalResult scanForInterval() throws HttpException {
        return new IntervalResult(this);
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
            if (hasStart()) {
                intv.setStart(start);
            }
            if (hasStop()) {
                intv.setEnd(stop);
            }
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
    
    /**
     * returns the body of the http request
     * 
     */
    public ByteBuf getRequestContent() {
        return httpRequest.content();
    }
}
