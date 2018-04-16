package org.yamcs.security;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.MediaType;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.security.JWT.JWTDecodeException;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.rest.RestRequest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.util.CharsetUtil;

/**
 * Default authentication module that supports two HTTP-based authentication mechanisms:
 * <ul>
 * <li>Basic: password credentials directly provided in request header
 * <li>Bearer: time-limited access token (issued via OAuth2Handler) provided in request header
 * </ul>
 * 
 * This module has good synergy with both the {@link YamlRealm} and the {@link LdapRealm}.
 * 
 * @author nm
 */
public class DefaultAuthModule implements AuthModule {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthModule.class);
    private static final String AUTH_TYPE_BASIC = "Basic ";
    private static final String AUTH_TYPE_BEARER = "Bearer ";

    private final Realm realm;
    private String realmName;
    private String secretKey;
    // time to cache a user entry
    static final int PRIV_CACHE_TIME = 30 * 1000;
    // time to cache a certificate to username mapping
    private final ConcurrentHashMap<AuthenticationToken, Future<User>> cache = new ConcurrentHashMap<>();

    public DefaultAuthModule(Map<String, Object> config) {
        String realmClass = YConfiguration.getString(config, "realm");
        realm = loadRealm(realmClass);

        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
        secretKey = yconf.getString("secretKey");
    }

    public Realm getRealm() {
        return realm;
    }

    private Realm loadRealm(String realmClass) throws ConfigurationException {
        // load the specified class;
        Realm realm;
        try {
            realm = (Realm) Realm.class.getClassLoader().loadClass(realmClass).newInstance();
            realmName = realm.getClass().getSimpleName();
        } catch (Exception e) {
            throw new ConfigurationException("Unable to load the realm class: " + realmClass, e);
        }
        return realm;
    }

    /**
     * @return the roles of the calling user
     */
    @Override
    public String[] getRoles(final AuthenticationToken authenticationToken) {
        // Load user and read roles from result
        User user = getUser(authenticationToken);
        if (user == null) {
            return null;
        }
        return user.getRoles();
    }

    @Override
    public CompletableFuture<AuthenticationToken> authenticateHttp(ChannelHandlerContext ctx, HttpRequest req) {
        if (req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            String authorizationHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorizationHeader.startsWith(AUTH_TYPE_BASIC)) { // Exact case only
                return handleBasicAuth(ctx, req);
            } else if (authorizationHeader.startsWith(AUTH_TYPE_BEARER)) {
                return handleBearerAuth(ctx, req);
            } else {
                return completedExceptionally(
                        new BadRequestException("Unsupported Authorization header '" + authorizationHeader + "'"));
            }
        } else if (req.headers().contains(HttpHeaderNames.COOKIE)) {
            return handleCookie(ctx, req);
        } else {
            sendUnauthorized(ctx, req, "Missing 'Authorization' or 'Cookie' header");
            return completedExceptionally(new AuthenticationPendingException());
        }
    }

    private CompletableFuture<AuthenticationToken> handleBasicAuth(ChannelHandlerContext ctx, HttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        String userpassEncoded = header.substring(AUTH_TYPE_BASIC.length());
        String userpassDecoded;
        try {
            userpassDecoded = new String(Base64.getDecoder().decode(userpassEncoded));
        } catch (IllegalArgumentException e) {
            return completedExceptionally(new BadRequestException("Could not decode Base64-encoded credentials"));
        }

        // Username is not allowed to contain ':', but passwords are
        String[] parts = userpassDecoded.split(":", 2);
        if (parts.length < 2) {
            return completedExceptionally(
                    new BadRequestException("Malformed username/password (Not separated by colon?)"));
        }
        AuthenticationToken token = new UsernamePasswordToken(parts[0], parts[1]);
        if (!realm.authenticate(token)) {
            sendUnauthorized(ctx, req, "Could not authenticate token against realm");
            return completedExceptionally(new AuthenticationPendingException());
        }

        return CompletableFuture.completedFuture(token);
    }

    private CompletableFuture<AuthenticationToken> handleBearerAuth(ChannelHandlerContext ctx, HttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        String jwt = header.substring(AUTH_TYPE_BEARER.length());
        return handleAccessToken(ctx, req, jwt);
    }

    private CompletableFuture<AuthenticationToken> handleCookie(ChannelHandlerContext ctx, HttpRequest req) {
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
            sendUnauthorized(ctx, req, "Missing 'Authorization' or 'Cookie' header");
            return completedExceptionally(new AuthenticationPendingException());
        }

        return handleAccessToken(ctx, req, jwt);
    }

    private CompletableFuture<AuthenticationToken> handleAccessToken(ChannelHandlerContext ctx, HttpRequest req,
            String jwt) {
        try {
            AuthenticationToken token = new AccessToken(jwt, secretKey);
            if (!realm.authenticate(token)) {
                sendUnauthorized(ctx, req, "Could not authenticate token against realm");
                return completedExceptionally(new AuthenticationPendingException());
            }

            return CompletableFuture.completedFuture(token);
        } catch (JWTDecodeException e) {
            sendUnauthorized(ctx, req, "Failed to decode JWT: " + e.getMessage());
            return completedExceptionally(new AuthenticationPendingException());
        }
    }

    static private CompletableFuture<AuthenticationToken> completedExceptionally(Exception e) {
        CompletableFuture<AuthenticationToken> cf = new CompletableFuture<>();
        cf.completeExceptionally(e);
        return cf;
    }

    @Override
    public User getUser(final AuthenticationToken authenticationToken) {
        while (true) {
            if (authenticationToken == null) {
                return null;
            }
            Future<User> f = cache.get(authenticationToken);
            if (f == null) {
                Callable<User> eval = () -> {
                    try {
                        // check the realm support the type of provided token
                        if (!realm.supports(authenticationToken)) {
                            log.error("Realm {} does not support authentication token of type {}", realmName,
                                    authenticationToken.getClass());
                            return null;
                        }
                        return realm.loadUser(authenticationToken);
                    } catch (Exception e) {
                        log.error("Unable to load user from realm {}", realmName, e);
                        return new User(authenticationToken);
                    }
                };
                FutureTask<User> ft = new FutureTask<>(eval);
                f = cache.putIfAbsent(authenticationToken, ft);
                if (f == null) {
                    f = ft;
                    ft.run();
                }
            }
            try {
                User u = f.get();
                if ((System.currentTimeMillis() - u.getLastUpdated().getTime()) < PRIV_CACHE_TIME) {
                    return u;
                }
                cache.remove(authenticationToken, f); // too old
            } catch (CancellationException e) {
                cache.remove(authenticationToken, f);
            } catch (ExecutionException e) {
                cache.remove(authenticationToken, f); // we don't cache exceptions
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("Unable to load user", e);
                return null;
            }
        }

    }

    /**
     *
     * @param type
     * @param privilege
     *            a opsname of tc, tm parameter or tm packet
     * @return true if the privilege is known and the current user has it.
     */
    @Override
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, PrivilegeType type, String privilege) {
        User user = getUser(authenticationToken);
        if (user == null) {
            return false;
        }
        return user.hasPrivilege(type, privilege);
    }

    @Override
    public boolean hasRole(final AuthenticationToken authenticationToken, String role) {
        // Load user and read role from result
        User user = getUser(authenticationToken);
        if (user == null) {
            return false;
        }
        return user.hasRole(role);
    }

    private ChannelFuture sendUnauthorized(ChannelHandlerContext ctx, HttpRequest request, String reason) {
        ByteBuf buf;
        MediaType mt = RestRequest.deriveTargetContentType(request);
        if (mt == MediaType.PROTOBUF) {
            RestExceptionMessage rem = RestExceptionMessage.newBuilder()
                    .setMsg(HttpResponseStatus.UNAUTHORIZED.toString()).build();
            buf = Unpooled.copiedBuffer(rem.toByteArray());
        } else {
            buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
        }
        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
        res.headers().set(HttpHeaderNames.WWW_AUTHENTICATE,
                "Basic realm=\"" + Privilege.getInstance().getAuthModuleName() + "\"");

        log.warn("{} {} {} [realm=\"{}\"]: {}", request.method(), request.uri(), res.status().code(),
                Privilege.getInstance().getAuthModuleName(), reason);
        return ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}
