package org.yamcs.web;

import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.User;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.web.JwtHelper.JwtDecodeException;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

/**
 * Checks the HTTP Authorization header for supported types. It also allows scanning bearer tokens from the Cookie
 * header specifically to support web socket requests coming from a browser (browsers do not allow setting custom
 * authorization headers on websocket requests).
 * <p>
 * This class maintains a cache from a JWT bearer token to the authenticated User object. This allows skipping the login
 * process as long as the bearer is valid.
 */
public class HttpAuthorizationChecker {

    private static final String AUTH_TYPE_BASIC = "Basic ";
    private static final String AUTH_TYPE_BEARER = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(HttpAuthorizationChecker.class);

    private final ConcurrentHashMap<String, User> jwtTokens = new ConcurrentHashMap<>();
    private int cleaningCounter = 0;

    public User verifyAuth(ChannelHandlerContext ctx, HttpRequest req) throws HttpException {
        cleaningCounter++;
        if (cleaningCounter == 1000) {
            cleaningCounter = 0;
            cleanupCache();
        }

        if (req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            String authorizationHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorizationHeader.startsWith(AUTH_TYPE_BASIC)) { // Exact case only
                return handleBasicAuth(ctx, req);
            } else if (authorizationHeader.startsWith(AUTH_TYPE_BEARER)) {
                return handleBearerAuth(ctx, req);
            } else {
                throw new BadRequestException("Unsupported Authorization header '" + authorizationHeader + "'");
            }
        } else if (req.headers().contains(HttpHeaderNames.COOKIE)) {
            return handleCookie(ctx, req);
        } else {
            throw new UnauthorizedException("Missing 'Authorization' or 'Cookie' header");
        }
    }

    public void storeTokenToUserMapping(String jwtToken, User user) {
        jwtTokens.put(jwtToken, user);
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
            return SecurityStore.getInstance().login(token).get();
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
        String jwt = header.substring(AUTH_TYPE_BEARER.length());
        return handleAccessToken(ctx, req, jwt);
    }

    private User handleCookie(ChannelHandlerContext ctx, HttpRequest req) throws UnauthorizedException {
        HttpHeaders headers = req.headers();
        String jwt = null;
        Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(headers.get(HttpHeaderNames.COOKIE));
        for (Cookie c : cookies) {
            if ("access_token".equalsIgnoreCase(c.name())) {
                jwt = c.value();
                break;
            }
        }

        if (jwt == null) {
            throw new UnauthorizedException("Missing 'Authorization' or 'Cookie' header");
        }

        return handleAccessToken(ctx, req, jwt);
    }

    private User handleAccessToken(ChannelHandlerContext ctx, HttpRequest req, String jwt)
            throws UnauthorizedException {
        try {
            JwtToken jwtToken = new JwtToken(jwt, YamcsServer.getSecretKey());
            if (jwtToken.isExpired()) {
                jwtTokens.remove(jwt);
                throw new UnauthorizedException("Token expired");
            }
            User cachedUser = jwtTokens.get(jwt);
            if (cachedUser == null) {
                log.warn("Got an invalid JWT token");
                throw new UnauthorizedException("Invalid JWT token");
            }

            if (!SecurityStore.getInstance().verifyValidity(cachedUser)) {
                jwtTokens.remove(jwt);
                throw new UnauthorizedException("Could not verify token");
            }

            return cachedUser;
        } catch (JwtDecodeException e) {
            throw new UnauthorizedException("Failed to decode JWT: " + e.getMessage());
        }
    }

    private void cleanupCache() {
        for (String jwt : jwtTokens.keySet()) {
            try {
                JwtToken jwtToken = new JwtToken(jwt, YamcsServer.getSecretKey());
                if (jwtToken.isExpired()) {
                    jwtTokens.remove(jwt);
                }
            } catch (JwtDecodeException e) {
                jwtTokens.remove(jwt);
            }
        }
    }
}
