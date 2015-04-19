package org.yamcs.web.rest;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When an unsupported HTTP method was used for a
 * specific path
 */
public class MethodNotAllowedException extends RestException {

    public MethodNotAllowedException(HttpMethod method) {
        super("Unsupported http method '" + method + "' for this path");
    }

    @Override
    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.METHOD_NOT_ALLOWED;
    }
}
