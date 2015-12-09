package org.yamcs.web;

import org.yamcs.web.rest.RestRequest;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When a resource (only the part identified by the request uri) could not be found
 */
public class NotFoundException extends HttpException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(RestRequest request) {
        super("No resource named '"+ request.getFullPathWithoutQueryString() +"'");
    }
    
    public NotFoundException(RestRequest request, String message) {
        super("No resource named '" + request.getFullPathWithoutQueryString() + "' (" + message + ")");
    }

    public NotFoundException(Throwable t) {
        super(t.getMessage(), t);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.NOT_FOUND;
    }
}
