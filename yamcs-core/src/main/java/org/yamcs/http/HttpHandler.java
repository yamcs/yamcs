package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;

import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.security.AbstractHttpRequestAuthModule;
import org.yamcs.security.AbstractHttpRequestAuthModule.HttpRequestToken;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.User;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.utils.Mimetypes;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

public abstract class HttpHandler {

    protected static final Mimetypes MIME = Mimetypes.getInstance();
    private static final String AUTH_TYPE_BASIC = "Basic ";
    private static final String AUTH_TYPE_BEARER = "Bearer ";

    protected final Log log = new Log(getClass());

    public abstract boolean requireAuth();

    public abstract void handle(HandlerContext ctx);

    final void handle(ChannelHandlerContext ctx, HttpRequest msg) {
        User user = null;
        if (requireAuth()) {
            user = authorizeUser(ctx, msg);
            ctx.channel().attr(HttpRequestHandler.CTX_USERNAME).set(user.getName());
        }

        doHandle(ctx, msg, user);
    }

    protected void doHandle(ChannelHandlerContext ctx, HttpRequest msg, User user) {
        var contextPath = ctx.channel().attr(HttpRequestHandler.CTX_CONTEXT_PATH).get();
        try {
            handle(new HandlerContext(contextPath, ctx, msg, user));
        } catch (Throwable t) {
            if (!(t instanceof HttpException)) {
                t = new InternalServerErrorException(t);
            }

            var e = (HttpException) t;
            if (e.isServerError()) {
                log.error("Responding '{}': {}", e.getStatus(), e.getMessage(), e);
            } else {
                log.warn("Responding '{}': {}", e.getStatus(), e.getMessage());
            }
            HttpRequestHandler.sendPlainTextError(ctx, msg, e.getStatus());
        }
    }

    private User authorizeUser(ChannelHandlerContext ctx, HttpRequest req) throws HttpException {
        var securityStore = YamcsServer.getServer().getSecurityStore();
        if (securityStore.isEnabled()) {
            // Handle common case first: presence of an "Authorization" header
            if (req.headers().contains(AUTHORIZATION)) {
                String authorizationHeader = req.headers().get(AUTHORIZATION);
                if (authorizationHeader.startsWith(AUTH_TYPE_BASIC)) { // Exact case only
                    return handleBasicAuth(ctx, req);
                } else if (authorizationHeader.startsWith(AUTH_TYPE_BEARER)) {
                    return handleBearerAuth(ctx, req);
                } else {
                    throw new BadRequestException("Unsupported Authorization header '" + authorizationHeader + "'");
                }
            }

            // Instances of AbstractHttpRequestAuthModule derive the user
            // from custom HTTP request information, typically headers.
            var isHttpRequestAuth = securityStore.getAuthModules().stream().anyMatch(module -> {
                return module instanceof AbstractHttpRequestAuthModule
                        && ((AbstractHttpRequestAuthModule) module).handles(ctx, req);
            });
            if (isHttpRequestAuth) {
                try {
                    var token = new HttpRequestToken(ctx, req);
                    var authenticationInfo = securityStore.login(token).get();
                    return securityStore.getUserFromCache(authenticationInfo.getUsername());
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

            // Last resort:
            // There may be an access token in the cookie. This use case is added because
            // of web socket requests coming from the browser where it is not possible to
            // set custom authorization headers. It'd be interesting if we communicate the
            // access token via the websocket subprotocol instead (e.g. via temp. route).
            String accessToken = getAccessTokenFromCookie(req);
            if (accessToken != null) {
                return handleAccessToken(ctx, req, accessToken);
            }
        }

        if (securityStore.getGuestUser().isActive()) {
            return securityStore.getGuestUser();
        }

        throw new UnauthorizedException("Missing authentication");
    }

    public static String getAccessTokenFromCookie(HttpRequest req) {
        HttpHeaders headers = req.headers();
        if (headers.contains(COOKIE)) {
            Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(headers.get(COOKIE));
            for (Cookie c : cookies) {
                if ("access_token".equalsIgnoreCase(c.name())) {
                    return c.value();
                }
            }
        }
        return null;
    }

    private User handleBasicAuth(ChannelHandlerContext ctx, HttpRequest req) throws HttpException {
        String header = req.headers().get(AUTHORIZATION);
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
            var securityStore = YamcsServer.getServer().getSecurityStore();
            AuthenticationToken token = new UsernamePasswordToken(parts[0], parts[1].toCharArray());
            AuthenticationInfo authenticationInfo = securityStore.login(token).get();
            return securityStore.getUserFromCache(authenticationInfo.getUsername());
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

    private User handleBearerAuth(ChannelHandlerContext ctx, HttpRequest req) throws UnauthorizedException {
        String header = req.headers().get(AUTHORIZATION);
        String accessToken = header.substring(AUTH_TYPE_BEARER.length());
        return handleAccessToken(ctx, req, accessToken);
    }

    private User handleAccessToken(ChannelHandlerContext ctx, HttpRequest req, String accessToken)
            throws UnauthorizedException {
        var httpServer = YamcsServer.getServer().getGlobalService(HttpServer.class);
        var tokenStore = httpServer.getTokenStore();
        var authenticationInfo = tokenStore.verifyAccessToken(accessToken);
        var securityStore = YamcsServer.getServer().getSecurityStore();
        if (!securityStore.verifyValidity(authenticationInfo)) {
            tokenStore.revokeAccessToken(accessToken);
            throw new UnauthorizedException("Could not verify token");
        }

        return securityStore.getUserFromCache(authenticationInfo.getUsername());
    }
}
