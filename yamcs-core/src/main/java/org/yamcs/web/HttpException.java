package org.yamcs.web;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Default HTTP exception. Makes it easier for handlers to just throw errors upwards
 */
public abstract class HttpException extends Exception {
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
}
