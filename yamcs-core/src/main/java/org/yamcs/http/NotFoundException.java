package org.yamcs.http;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * When a resource (only the part identified by the request uri) could not be found.
 * <p>
 * Do *not* use this for anything else. For example if a query parameter refers to something that does not exist, use a
 * BadRequestException instead.
 */
public class NotFoundException extends HttpException {
    private static final long serialVersionUID = 1L;

    public NotFoundException() {
        super("Resource not found");
    }

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(Throwable t) {
        super(t.getMessage(), t);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.NOT_FOUND;
    }
}
