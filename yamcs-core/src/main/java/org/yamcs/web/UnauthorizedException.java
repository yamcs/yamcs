package org.yamcs.web;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When the request was valid, but did not pass authentication.
 * (only covers auth, not authz! Use something like 403 or 404 for authz)
 */
public class UnauthorizedException extends HttpException {
    private static final long serialVersionUID = 1L;
    
    public UnauthorizedException() {
        super();
    }

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.UNAUTHORIZED;
    }
}
