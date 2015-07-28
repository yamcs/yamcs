package org.yamcs.web.rest;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When an unsupported HTTP method was used for a
 * specific path
 */
public class MethodNotAllowedException extends RestException {
    private static final long serialVersionUID = 1L;

    public MethodNotAllowedException(RestRequest req) {
        super(String.format("Unsupported http method '%s' for path '%s'",
                req.getHttpRequest().getMethod(),
                req.getHttpRequest().getUri()));
    }

    @Override
    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.METHOD_NOT_ALLOWED;
    }
}
