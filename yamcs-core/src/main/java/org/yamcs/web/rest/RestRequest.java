package org.yamcs.web.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.TimeInterval;
import org.yamcs.api.MediaType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.Router.RouteMatch;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.ssl.SslHandler;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * Encapsulates everything to do with one Rest Request. Object is gc-ed, when request ends.
 */
public class RestRequest {
    
    public enum Option {
        NO_LINK;
    }
    
    private ChannelHandlerContext channelHandlerContext;
    private FullHttpRequest httpRequest;
    private QueryStringDecoder qsDecoder;
    private AuthenticationToken token;
    private RouteMatch routeMatch;
    private static JsonFactory jsonFactory = new JsonFactory();
    
    public RestRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest, QueryStringDecoder qsDecoder, AuthenticationToken token) {
        this.channelHandlerContext = channelHandlerContext;
        this.httpRequest = httpRequest;
        this.token = token;
        this.qsDecoder = qsDecoder;
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
        return routeMatch.regexMatch.group(name);
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
    
    public String getHeader(String name) {
        return httpRequest.headers().get(name);
    }
    
    /**
     * Experimental feature to gather common parameters. Should also be made to include the pretty-flag
     */
    public Set<Option> getOptions() {
        Set<Option> options = new HashSet<>(2);
        if (getQueryParameterAsBoolean("nolink", false)) {
            options.add(Option.NO_LINK);
        }
        return options;
    }
    
    /**
     * Matches the content type on either the Accept header or a 'format' query param.
     * Should probably better be integrated with the deriveTargetContentType setting.
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
            return getHttpRequest().headers().contains(Names.ACCEPT)
                    && mediaType.is(getHttpRequest().headers().get(Names.ACCEPT));
        }
    }
    
    /**
     * Returns the authenticated user. Or <tt>null</tt> if the user is not authenticated.
     */
    public User getUser() {
        return Privilege.getInstance().getUser(token);
    }
    
    /**
     * Returns the username of the authenticated user. Or {@link Privilege#getDefaultUser()} if the user
     * is not authenticated.
     */
    public String getUsername() {
        User user = getUser();
        return (user != null) ? user.getPrincipalName() : Privilege.getDefaultUser();
    }
    
    public AuthenticationToken getAuthToken() {
        return token;
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
        if (param.isEmpty()) return null;
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
    
    public HttpRequest getHttpRequest() {
        return httpRequest;
    }
    
    /**
     * Returns a new Json Generator that will have pretty-printing enabled if the original request specified this.
     */
    public JsonGenerator createJsonGenerator(OutputStream out) throws IOException {
        JsonGenerator generator = jsonFactory.createGenerator(out, JsonEncoding.UTF8);
        if (qsDecoder.parameters().containsKey("pretty")) {
            if (hasQueryParameter("pretty") && getQueryParameterAsBoolean("pretty")) {
                generator.useDefaultPrettyPrinter();
            }
        } else {
            // Pretty by default
            generator.useDefaultPrettyPrinter();
        }
        return generator;
    }
    
    public boolean hasBody() {
        return HttpHeaders.getContentLength(httpRequest) > 0;
    }
    
    /**
     * Deserializes the incoming message extracted from the body. This does not
     * care about what the HTTP method is. Any required checks should be done
     * elsewhere.
     * <p>
     * This method is only able to read JSON or Protobuf, the two auto-supported
     * serialization mechanisms. If a certain operation needs to read anything
     * else, it should check for that itself, and then use
     * {@link #bodyAsInputStream()}.
     */
    public <T extends MessageLite.Builder> T bodyAsMessage(Schema<T> sourceSchema) throws BadRequestException {
        MediaType sourceContentType = deriveSourceContentType();
        InputStream cin = bodyAsInputStream();
        T msg = sourceSchema.newMessage();
        // Allow for empty body, otherwise user has to specify '{}'
        if (HttpHeaders.getContentLength(httpRequest) > 0) {
            try {
                if (MediaType.PROTOBUF.equals(sourceContentType)) {
                    msg.mergeFrom(cin);
                } else {
                    JsonIOUtil.mergeFrom(cin, msg, sourceSchema, false);
                }
            } catch(IOException|NullPointerException e) {
                throw new BadRequestException(e);
            } finally {
                // GPB's mergeFrom does not close the stream, not sure about JsonIOUtil
                try { cin.close(); } catch (IOException e) {}
            }
        }
        return msg;
    }
    
    public InputStream bodyAsInputStream() {
        return new ByteBufInputStream(httpRequest.content());
    }
    
    /**
     * Derives the content type of the incoming request. Returns either JSON or
     * BINARY in that order.
     */
    public MediaType deriveSourceContentType() {
        if (httpRequest.headers().contains(Names.CONTENT_TYPE)) {
            String declaredContentType = httpRequest.headers().get(Names.CONTENT_TYPE);
            if (MediaType.JSON.is(declaredContentType)) {
                return MediaType.JSON;
            } else if (MediaType.PROTOBUF.is(declaredContentType)) {
                return MediaType.PROTOBUF;
            }
        }

        // Assume default for simplicity
        return MediaType.JSON;
    }

    /**
     * Derives an applicable content type for the output. This tries to match
     * JSON or BINARY media types with the ACCEPT header, else it will revert to
     * the (derived) source content type.
     */
    public MediaType deriveTargetContentType() {
        return deriveTargetContentType(httpRequest);
    }
    
    
    public static MediaType deriveTargetContentType(HttpRequest httpRequest) {
        if (httpRequest.headers().contains(Names.ACCEPT)) {
            String acceptedContentType = httpRequest.headers().get(Names.ACCEPT);
            if (MediaType.JSON.is(acceptedContentType)) {
                return MediaType.JSON;
            } else if (MediaType.PROTOBUF.is(acceptedContentType)) {
                return MediaType.PROTOBUF;
            }
        } else if (httpRequest.headers().contains(Names.CONTENT_TYPE)) {
            String declaredContentType = httpRequest.headers().get(Names.CONTENT_TYPE);
            if (MediaType.JSON.is(declaredContentType)) {
                return MediaType.JSON;
            } else if (MediaType.PROTOBUF.is(declaredContentType)) {
                return MediaType.PROTOBUF;
            }
        }
        //if none of accept or content_type specified, just assume JSON
        return MediaType.JSON;
    }
    
    public String getBaseURL() {
        String scheme = isSSL() ? "https://" : "http://";
        String host = getHeader(HttpHeaders.Names.HOST);
        return (host != null) ? scheme + host : "";
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
    
    public IntervalResult scanForInterval() throws HttpException {
        return new IntervalResult(this);
    }
    
    public String getApiURL() {
        return getBaseURL() + "/api";
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
