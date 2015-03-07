package org.yamcs.web.rest;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Default rest exception. Makes it easier for handlers to not just throw anything upwards, but think about
 * whether something is a bad request, or any other subclass.
 * <p>
 * If not subclassed, this should lead to a response code 500.
 */
public class RestException extends Exception {

    public RestException(Throwable t) {
        super(t);
    }

    public RestException(String message) {
        super(message);
    }

    public RestException(String message, Throwable t) {
        super(message, t);
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }
}
