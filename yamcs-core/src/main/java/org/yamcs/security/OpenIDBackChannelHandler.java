package org.yamcs.security;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.YamcsServer;
import org.yamcs.http.BodyHandler;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.NotFoundException;
import org.yamcs.security.JwksVerifier.JwtVerificationException;

import io.netty.handler.codec.http.DefaultHttpResponse;

public class OpenIDBackChannelHandler extends BodyHandler {

    private OpenIDAuthModule authModule;

    public OpenIDBackChannelHandler(OpenIDAuthModule authModule) {
        this.authModule = authModule;
    }

    @Override
    public boolean requireAuth() {
        return false;
    }

    @Override
    public void handle(HandlerContext ctx) {
        var path = ctx.getPathWithoutContext();
        if (path.equals("/openid/backchannel-logout")) {
            handleBackChannelLogout(ctx);
            return;
        }
        throw new NotFoundException();
    }

    private void handleBackChannelLogout(HandlerContext ctx) {
        ctx.requirePOST();
        ctx.requireFormEncoding();

        var request = new OpenIDBackChannelLogoutRequest(ctx);
        var logoutToken = request.getLogoutToken();

        // Validation may need to retrieve the signing keys from the OpenID server
        var executor = YamcsServer.getServer().getThreadPoolExecutor();
        CompletableFuture.runAsync(() -> {
            try {
                authModule.handleLogoutToken(logoutToken);
            } catch (JwtVerificationException e) {
                throw new CompletionException(e);
            }
        }, executor).whenComplete((result, err) -> {
            if (err == null) {
                var response = new DefaultHttpResponse(HTTP_1_1, OK);
                response.headers().set(CACHE_CONTROL, NO_STORE);
                ctx.sendResponse(response);
                return;
            }

            var cause = (err instanceof CompletionException) ? err.getCause() : err;
            if (cause instanceof JwtVerificationException) {
                log.info("Rejecting back-channel logout request: {}", cause.getMessage());
                HttpRequestHandler.sendPlainTextError(ctx.getNettyChannelHandlerContext(),
                        ctx.getNettyHttpRequest(), BAD_REQUEST, cause.getMessage());
            } else {
                log.error("Failed to handle back-channel logout request", cause);
                HttpRequestHandler.sendPlainTextError(ctx.getNettyChannelHandlerContext(),
                        ctx.getNettyHttpRequest(), INTERNAL_SERVER_ERROR);
            }
        });
    }
}
