package org.yamcs.web;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When there was an authz exception
 */
public class ForbiddenException extends HttpException {
    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(Throwable t) {
        super("Forbidden", t);
    }

    public ForbiddenException(String message, Throwable t) {
        super(message, t);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.FORBIDDEN;
    }
}
