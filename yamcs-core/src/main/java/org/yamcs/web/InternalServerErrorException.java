package org.yamcs.web;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Something really wrong and unexpected occurred on the server. A bug.
 */
public class InternalServerErrorException extends HttpException {
    private static final long serialVersionUID = 1L;

    public InternalServerErrorException(Throwable t) {
        super(t);
    }

    public InternalServerErrorException(String message) {
        super(message);
    }

    public InternalServerErrorException(String message, Throwable t) {
        super(message, t);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }
}
