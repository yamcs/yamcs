package org.yamcs.security;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.YamcsServer;
import org.yamcs.http.BodyHandler;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.NotFoundException;
import org.yamcs.security.JwksVerifier.JwtVerificationException;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;

public class OpenIDHandler extends BodyHandler {

    private OpenIDAuthModule authModule;

    public OpenIDHandler(OpenIDAuthModule authModule) {
        this.authModule = authModule;
    }

    @Override
    public boolean requireAuth() {
        return false;
    }

    @Override
    public void handle(HandlerContext ctx) {
        var path = ctx.getPathWithoutContext();
        if (path.equals("/openid/authorize")) {
            handleAuthorize(ctx);
            return;
        } else if (path.equals("/openid/backchannel-logout")) {
            handleBackChannelLogout(ctx);
            return;
        }
        throw new NotFoundException();
    }

    /**
     * Redirects the browser to the authorization endpoint of the OpenID server, passing along the query parameters of
     * the request.
     * <p>
     * The endpoint is resolved at request time, so that clients do not need to know it in advance. This allows
     * recovering once the OpenID server becomes reachable, without requiring clients to reload their configuration.
     */
    private void handleAuthorize(HandlerContext ctx) {
        ctx.requireGET();

        var authorizationEndpoint = authModule.getAuthorizationEndpoint();
        if (authorizationEndpoint == null) {
            var vars = new HashMap<String, Object>();
            vars.put("contextPath", ctx.getContextPath());
            ctx.render(SERVICE_UNAVAILABLE, "/auth/templates/openid-unavailable.html", vars);
            return;
        }

        var rawQuery = new QueryStringDecoder(ctx.getNettyHttpRequest().uri()).rawQuery();
        ctx.sendRedirect(buildAuthorizationUrl(authorizationEndpoint, rawQuery));
    }

    private static String buildAuthorizationUrl(String authorizationEndpoint, String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return authorizationEndpoint;
        }
        var separator = authorizationEndpoint.contains("?") ? "&" : "?";
        return authorizationEndpoint + separator + rawQuery;
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
