package org.yamcs.security;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.BodyHandler;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.auth.JwtHelper;
import org.yamcs.http.auth.JwtHelper.JwtDecodeException;

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
        try {
            var claims = JwtHelper.decodeUnverified(logoutToken);
            var iss = claims.get("iss").getAsString();

            // Either sub or sid has to be present.
            //
            // If only sub is present, the logout should impact all
            // Yamcs sessions for that user identity.
            //
            // If both sub and sid are present, the logout should
            // cover only the Yamcs sessions matching the OpenID sid.

            String sub = null;
            if (claims.has("sub")) {
                sub = claims.get("sub").getAsString();
            }

            String sid = null;
            if (claims.has("sid")) {
                sid = claims.get("sid").getAsString();
            }

            if (sid != null) {
                log.debug("Back-channel logout for sid={}", sid);
                authModule.logoutByOidcSessionId(iss, sid);
            } else {
                log.debug("Back-channel logout for sub={}", sub);
                authModule.logoutByOidcSubject(iss, sub);
            }
        } catch (JwtDecodeException e) {
            throw new BadRequestException(e);
        }

        var response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers().set(CACHE_CONTROL, NO_STORE);
        ctx.sendResponse(response);
    }
}
