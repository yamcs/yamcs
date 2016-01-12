package org.yamcs.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When an unsupported HTTP method was used for a
 * specific path
 */
public class MethodNotAllowedException extends HttpException {
    
    private static final long serialVersionUID = 1L;
    
    private List<HttpMethod> allowedMethods;

    public MethodNotAllowedException(HttpMethod method, String uri, Collection<HttpMethod> allowedMethods) {
        super(String.format("Unsupported HTTP method '%s' for resource '%s'", method, uri));
        this.allowedMethods = new ArrayList<>(allowedMethods);
        Collections.sort(this.allowedMethods);
    }
    
    public List<HttpMethod> getAllowedMethods() {
        return allowedMethods;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.METHOD_NOT_ALLOWED;
    }
}
