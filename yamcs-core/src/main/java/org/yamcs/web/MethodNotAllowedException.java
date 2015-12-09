package org.yamcs.web;

import org.yamcs.web.rest.RestRequest;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When an unsupported HTTP method was used for a
 * specific path
 */
public class MethodNotAllowedException extends HttpException {
    private static final long serialVersionUID = 1L;

    public MethodNotAllowedException(RestRequest req) {
        super(String.format("Unsupported http method '%s' for path '%s'",
                req.getHttpRequest().getMethod(),
                req.getHttpRequest().getUri()));
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.METHOD_NOT_ALLOWED;
    }
}
