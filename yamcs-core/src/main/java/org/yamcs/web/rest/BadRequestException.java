package org.yamcs.web.rest;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When there were errors in interpreting the request
 */
public class BadRequestException extends RestException {
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(Throwable t) {
        super(t.getMessage(), t);
    }

    @Override
    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.BAD_REQUEST;
    }
}
