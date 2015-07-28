package org.yamcs.web.rest;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Default rest exception. Makes it easier for handlers to not just throw anything upwards, but think about
 * whether something is a bad request, or any other subclass.
 */
public abstract class RestException extends Exception {
    private static final long serialVersionUID = 1L;

    public RestException(Throwable t) {
        super(t);
    }

    public RestException(String message) {
        super(message);
    }

    public RestException(String message, Throwable t) {
        super(message, t);
    }

    public abstract HttpResponseStatus getHttpResponseStatus();
}
