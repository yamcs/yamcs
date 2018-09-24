package org.yamcs.web;

import org.yamcs.security.User;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Used to pass information from one netty handler, to another
 */
public class HttpRequestInfo {

    private final HttpMethod method;
    private final String uri;
    private final HttpHeaders headers;

    // optional
    private String yamcsInstance;
    private String processor;
    private User user;

    public HttpRequestInfo(HttpRequest req) {
        method = req.method();
        uri = req.uri();
        headers = req.headers();
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public void setYamcsInstance(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public void setProcessor(String processor) {
        this.processor = processor;
    }

    public String getProcessor() {
        return processor;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
