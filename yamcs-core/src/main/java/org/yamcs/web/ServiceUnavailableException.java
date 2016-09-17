package org.yamcs.web;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Something really wrong and unexpected occurred on the server. A bug.
 */
public class ServiceUnavailableException extends HttpException {
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(Throwable t) {
        super(t);
    }

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable t) {
        super(message, t);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.SERVICE_UNAVAILABLE;
    }
}
