package org.yamcs.http.auth;

import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.yamcs.YamcsServer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.UnauthorizedException;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.User;
import org.yamcs.security.UsernamePasswordToken;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

/**
 * Checks the HTTP Authorization or Cookie header for supported types.
 */
public class HttpAuthorizationChecker {

    private static final String AUTH_TYPE_BASIC = "Basic ";
    private static final String AUTH_TYPE_BEARER = "Bearer ";

    private SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
    private TokenStore tokenStore;

    public HttpAuthorizationChecker(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    public User verifyAuth(ChannelHandlerContext ctx, HttpRequest req) throws HttpException {
        if (req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            String authorizationHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorizationHeader.startsWith(AUTH_TYPE_BASIC)) { // Exact case only
                return handleBasicAuth(ctx, req);
            } else if (authorizationHeader.startsWith(AUTH_TYPE_BEARER)) {
                return handleBearerAuth(ctx, req);
            } else {
                throw new BadRequestException("Unsupported Authorization header '" + authorizationHeader + "'");
            }
        }

        // There may be an access token in the cookie. This use case is added because
        // of web socket requests coming from the browser where it is not possible to
        // set custom authorization headers. It'd be interesting if we communicate the
        // access token via the websocket subprotocol instead (e.g. via temp. route).
        String accessToken = getAccessTokenFromCookie(req);
        if (accessToken != null) {
            return handleAccessToken(ctx, req, accessToken);
        }

        if (securityStore.getGuestUser().isActive()) {
            return securityStore.getGuestUser();
        } else {
            throw new UnauthorizedException("Missing 'Authorization' or 'Cookie' header");
        }
    }

    private String getAccessTokenFromCookie(HttpRequest req) {
        HttpHeaders headers = req.headers();
        if (headers.contains(HttpHeaderNames.COOKIE)) {
            Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(headers.get(HttpHeaderNames.COOKIE));
            for (Cookie c : cookies) {
                if ("access_token".equalsIgnoreCase(c.name())) {
                    return c.value();
                }
            }
        }
        return null;
    }

    private User handleBasicAuth(ChannelHandlerContext ctx, HttpRequest req) throws HttpException {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        String userpassEncoded = header.substring(AUTH_TYPE_BASIC.length());
        String userpassDecoded;
        try {
            userpassDecoded = new String(Base64.getDecoder().decode(userpassEncoded));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Could not decode Base64-encoded credentials");
        }

        // Username is not allowed to contain ':', but passwords are
        String[] parts = userpassDecoded.split(":", 2);
        if (parts.length < 2) {
            throw new BadRequestException("Malformed username/password (Not separated by colon?)");
        }

        try {
            AuthenticationToken token = new UsernamePasswordToken(parts[0], parts[1].toCharArray());
            AuthenticationInfo authenticationInfo = securityStore.login(token).get();
            return securityStore.getDirectory().getUser(authenticationInfo.getUsername());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AuthenticationException) {
                throw new UnauthorizedException(e.getCause().getMessage());
            } else {
                throw new InternalServerErrorException(e.getCause());
            }
        }
    }

    private User handleBearerAuth(ChannelHandlerContext ctx, HttpRequest req)
            throws UnauthorizedException {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        String accessToken = header.substring(AUTH_TYPE_BEARER.length());
        return handleAccessToken(ctx, req, accessToken);
    }

    private User handleAccessToken(ChannelHandlerContext ctx, HttpRequest req, String accessToken)
            throws UnauthorizedException {
        AuthenticationInfo authenticationInfo = tokenStore.verifyAccessToken(accessToken);
        if (!securityStore.verifyValidity(authenticationInfo)) {
            tokenStore.revokeAccessToken(accessToken);
            throw new UnauthorizedException("Could not verify token");
        }

        return securityStore.getDirectory().getUser(authenticationInfo.getUsername());
    }
}
