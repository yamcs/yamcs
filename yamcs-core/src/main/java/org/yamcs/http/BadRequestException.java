package org.yamcs.http;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When there were errors in interpreting the request
 */
public class BadRequestException extends HttpException {
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable t) {
        super(message, t);
    }

    public BadRequestException(Throwable t) {
        super(t.getMessage(), t);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.BAD_REQUEST;
    }
}
