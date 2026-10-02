package org.yamcs.http;

import java.util.Set;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class HttpUtils {

    private static final Set<String> SENSITIVE_QUERY_PARAMS = Set.of("code", "access_token", "id_token",
            "refresh_token");

    public static final FullHttpResponse CONTINUE_RESPONSE = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

    /**
     * Returns the path for the given HTTP request. This path does not contain any query string information, and the
     * leading context path is removed.
     */
    public static String getPathWithoutContext(HttpRequest req, String contextPath) {
        String path = removeQueryString(req.uri());
        if (contextPath.isEmpty()) {
            return path;
        } else {
            if (path.startsWith(contextPath)) {
                var stripped = path.substring(contextPath.length());
                return stripped.length() == 0 ? "/" : stripped;
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

    /**
     * Returns the given uri with the values of sensitive query parameters (such as authorization codes) masked, for
     * use in log output.
     */
    public static String redactUri(String uri) {
        int idx = uri.indexOf('?');
        if (idx == -1) {
            return uri;
        }
        var sb = new StringBuilder(uri.length()).append(uri, 0, idx + 1);
        String[] params = uri.substring(idx + 1).split("&", -1);
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append('&');
            }
            String param = params[i];
            int eq = param.indexOf('=');
            if (eq != -1 && SENSITIVE_QUERY_PARAMS.contains(param.substring(0, eq))) {
                sb.append(param, 0, eq + 1).append("***");
            } else {
                sb.append(param);
            }
        }
        return sb.toString();
    }

    private static String removeQueryString(String uri) {
        int idx = uri.indexOf('?');
        return (idx == -1) ? uri : uri.substring(0, idx);
    }
}
