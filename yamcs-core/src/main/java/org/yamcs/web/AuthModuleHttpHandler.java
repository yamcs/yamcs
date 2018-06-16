package org.yamcs.web;

import org.yamcs.security.AuthModule;
import org.yamcs.web.rest.Router;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * Interface to be implemented by {@link AuthModule} if they want to provide a handle for /auth/* requests 
 * (which are executed outside the normal authenticated scope)
 * 
 * Currently only one path can be registered;
 *  the {@link AuthHandler} could be extended to a different {@link Router} like functionality. 
 *
 */
public interface AuthModuleHttpHandler {
    /**
     * Returns the path part from /auth/path for which this handler will be invoked 
     * It should not contain any slashes
     */
    String path();
    
    /**
     * Handle the HTTP request
     * 
     * @param ctx
     * @param req
     */
    void handle(ChannelHandlerContext ctx, FullHttpRequest req);
    
}
