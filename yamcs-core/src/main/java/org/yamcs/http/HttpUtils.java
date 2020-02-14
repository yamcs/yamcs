package org.yamcs.http;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

public class HttpUtils {

    public static final FullHttpResponse EMPTY_BAD_REQUEST_RESPONSE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
            Unpooled.EMPTY_BUFFER);

    public static final FullHttpResponse CONTINUE_RESPONSE = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

    static {
        EMPTY_BAD_REQUEST_RESPONSE.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
    }

    /**
     * Returns the path for the given HTTP request. This path does not contain any query string information, and the
     * leading context path is removed.
     */
    public static String getPathWithoutContext(HttpRequest req, String contextPath) {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.uri());
        if (contextPath.isEmpty()) {
            return qsDecoder.path();
        } else {
            String uriWithContextPath = qsDecoder.path();
            if (uriWithContextPath.startsWith(contextPath)) {
                return uriWithContextPath.substring(contextPath.length());
            } else {
                throw new IllegalArgumentException("URI does not start with context path");
            }
        }
    }

    /**
     * Returns the uri for the given HTTP request, but with the context path removed. The query string (if any) remains
     * intact.
     */
    public static String getUriWithoutContext(HttpRequest req, String contextPath) {
        if (contextPath.isEmpty()) {
            return req.uri();
        } else {
            String uriWithContextPath = req.uri();
            if (uriWithContextPath.startsWith(contextPath)) {
                return uriWithContextPath.substring(contextPath.length());
            } else {
                throw new IllegalArgumentException("URI does not start with context path");
            }
        }
    }
}
