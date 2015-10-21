package org.yamcs.web.rest;

/**
 * Defines the basic contract of what a REST handler should abide to.
 */
public interface RestRequestHandler {
    
    /**
     * Returns the path handled by this handler.
     */
    String getPath();

    /**
     * Wraps all the logic that deals with a RestRequest. Requests should always
     * return something, which is why a return type is enforced. For handlers
     * that have to stream their response, use <tt>return null;</tt> to
     * explicitely turn off a forced response. See {@link ArchiveRequestHandler}
     * for an example of this.
     * 
     * @param pathOffset
     *            the path offset wherein this handler operates. Use this to
     *            correctly index into {@link RestRequest#getPathSegment(int)}
     */
    RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException;
    
    /**
     * Helper method to throw a BadRequestException on incorrect requests. This is some validation mechanism
     * beyond proto, where we try to keep things optional
     */
    default <T> T required(T object, String message) throws BadRequestException {
        if(object != null)
            return object;
        else
            throw new BadRequestException(message);
    }
}
