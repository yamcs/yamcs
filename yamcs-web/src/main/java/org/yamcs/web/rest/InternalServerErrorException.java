package org.yamcs.web.rest;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Something really wrong and unexpected occurred on the server. A bug.
 */
public class InternalServerErrorException extends RestException {

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
    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }
}
