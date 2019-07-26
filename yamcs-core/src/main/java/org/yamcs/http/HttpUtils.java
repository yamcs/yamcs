package org.yamcs.http;

import org.yamcs.api.MediaType;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;

public class HttpUtils {

    /**
     * Sets the content type header for the HTTP Response
     * 
     * @param response
     * 
     * @param type
     *            content type of file to extract
     */
    public static void setContentTypeHeader(HttpResponse response, MediaType type) {
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, type.toString());
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
