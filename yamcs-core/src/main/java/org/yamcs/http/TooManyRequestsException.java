package org.yamcs.http;

import io.netty.handler.codec.http.HttpResponseStatus;

public class TooManyRequestsException extends HttpException {
    private static final long serialVersionUID = 1L;

    public TooManyRequestsException() {
        super("Too many requests");
    }

    public TooManyRequestsException(String message) {
        super(message);
    }

    public TooManyRequestsException(Throwable t) {
        super(t.getMessage(), t);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.TOO_MANY_REQUESTS;
    }
}
