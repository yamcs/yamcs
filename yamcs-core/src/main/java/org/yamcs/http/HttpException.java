package org.yamcs.http;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Default HTTP exception.
 */
public abstract class HttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HttpException() {
        super();
    }

    public HttpException(Throwable t) {
        super(t);
    }

    public HttpException(String message) {
        super(message);
    }

    public HttpException(String message, Throwable t) {
        super(message, t);
    }

    public abstract HttpResponseStatus getStatus();

    public boolean isServerError() {
        int code = getStatus().code();
        return 500 <= code && code < 600;
    }
}
