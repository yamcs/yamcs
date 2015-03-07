package org.yamcs.web.rest;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * When there was an authz exception
 */
public class ForbiddenException extends RestException {

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
    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.FORBIDDEN;
    }
}
