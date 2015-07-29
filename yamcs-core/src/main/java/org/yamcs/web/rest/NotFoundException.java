package org.yamcs.web.rest;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When a resource (only the part identified by the request uri) could not be found
 */
public class NotFoundException extends RestException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(RestRequest request) {
        super("No resource named '"+ request.getHttpRequest().getUri() +"'");
    }

    public NotFoundException(Throwable t) {
        super(t.getMessage(), t);
    }

    @Override
    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.NOT_FOUND;
    }
}
