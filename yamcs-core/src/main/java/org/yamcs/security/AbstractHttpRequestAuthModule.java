package org.yamcs.security;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Base class for an {@link AuthModule} that identifies users based on an incoming HTTP request.
 */
public abstract class AbstractHttpRequestAuthModule implements AuthModule {

    /**
     * Returns true if this AuthModule is capable of handling the given HTTP request.
     */
    public abstract boolean handles(ChannelHandlerContext ctx, HttpRequest request);

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof HttpRequestToken) {
            var ctx = ((HttpRequestToken) token).ctx;
            var request = ((HttpRequestToken) token).request;
            if (handles(ctx, request)) {
                return getAuthenticationInfo(ctx, request);
            }
        }
        return null;
    }

    public abstract AuthenticationInfo getAuthenticationInfo(
            ChannelHandlerContext ctx, HttpRequest request) throws AuthenticationException;

    /**
     * Data holder for passing an {@link HttpRequest} to a login call.
     */
    public static class HttpRequestToken implements AuthenticationToken {

        private ChannelHandlerContext ctx;
        private HttpRequest request;

        public HttpRequestToken(ChannelHandlerContext ctx, HttpRequest request) {
            this.ctx = ctx;
            this.request = request;
        }
    }
}
