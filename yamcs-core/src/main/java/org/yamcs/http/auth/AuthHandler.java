package org.yamcs.http.auth;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.BodyHandler;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.HttpServer;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.UnauthorizedException;
import org.yamcs.http.api.IamApi;
import org.yamcs.http.auth.TokenStore.RefreshResult;
import org.yamcs.protobuf.AuthInfo;
import org.yamcs.protobuf.OpenIDConnectInfo;
import org.yamcs.protobuf.TokenResponse;
import org.yamcs.security.ApplicationCredentials;
import org.yamcs.security.AuthModule;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.AuthorizationException;
import org.yamcs.security.Directory;
import org.yamcs.security.OpenIDAuthModule;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SessionManager;
import org.yamcs.security.SpnegoAuthModule;
import org.yamcs.security.ThirdPartyAuthorizationCode;
import org.yamcs.security.User;
import org.yamcs.security.UserSession;
import org.yamcs.security.UsernamePasswordToken;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringEncoder;

/**
 * Adds servers-side support for OAuth 2 authorization flows for obtaining limited access to API functionality. The
 * resource server is assumed to be the same server as the authentication server.
 * <p>
 * Currently only one flow is supported:
 * <dl>
 * <dt>Resource Owner Password Credentials</dt>
 * <dd>User credentials are directly exchanged for access tokens.</dd>
 * </dl>
 */
public class AuthHandler extends BodyHandler {

    private static final SecureRandom RNG = new SecureRandom();

    // Cache for temporary authorization codes. This is an indermediate format provided
    // to browsers so that they can provide it to a server-side web application that
    // will exchange it for their id_token on our token endpoint.
    private static Cache<String, AuthenticationInfo> CODE_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS).build();

    private TokenStore tokenStore;

    public AuthHandler(HttpServer httpServer) {
        tokenStore = httpServer.getTokenStore();
    }

    @Override
    public boolean requireAuth() {
        return false;
    }

    @Override
    public void handle(HandlerContext ctx) {
        String path = ctx.getPathWithoutContext();
        if (path.equals("/auth")) {
            handleAuthInfoRequest(ctx);
            return;
        } else if (path.equals("/auth/assets/auth.css")) {
            ctx.sendResource("/auth/static/auth.css");
            return;
        } else if (path.equals("/auth/assets/yamcs300.png")) {
            ctx.sendResource("/auth/static/yamcs300.png");
            return;
        } else if (path.equals("/auth/authorize")) {
            handleAuthorize(ctx);
            return;
        } else if (path.equals("/auth/token")) {
            handleToken(ctx);
            return;
        } else if (path.equals("/auth/spnego")) {
            var spnegoAuthModule = getSecurityStore().getAuthModule(SpnegoAuthModule.class);
            if (spnegoAuthModule != null) {
                spnegoAuthModule.handle(ctx);
                return;
            }
        } else if (path.equals("/auth/actions/login")) {
            handleLoginAction(ctx);
            return;
        }

        throw new NotFoundException();
    }

    /**
     * Provides general auth information. This path is not secured because it's primary intended use is exactly to
     * determine whether Yamcs is secured or not (e.g. in order to detect if a login screen should be shown to the
     * user).
     */
    private void handleAuthInfoRequest(HandlerContext ctx) {
        ctx.requireGET();
        ctx.sendOK(createAuthInfo());
    }

    private void handleAuthorize(HandlerContext ctx) {
        ctx.requireMethod(HttpMethod.GET, HttpMethod.POST);
        OpenIDAuthenticationRequest request = new OpenIDAuthenticationRequest(ctx);
        showLoginForm(ctx, request);
    }

    private void handleLoginAction(HandlerContext ctx) {
        ctx.requirePOST();
        ctx.requireFormEncoding();

        LoginRequest request = new LoginRequest(ctx);

        AuthenticationToken token = request.getUsernamePasswordToken();
        getSecurityStore().login(token).whenComplete((info, err) -> {
            if (err != null) {
                if (err instanceof AuthenticationException || err instanceof AuthorizationException) {
                    log.info("Denying access to '" + request.getUsername() + "': " + err.getMessage());
                    showLoginError(ctx, request, "Invalid username or password");
                } else {
                    log.error("Unexpected error while attempting user login", err);
                    showLoginError(ctx, request, "Server Error");
                }
            } else {
                redirectWithCode(ctx, info, request);
            }
        });
    }

    public static AuthInfo createAuthInfo() {
        AuthInfo.Builder infob = AuthInfo.newBuilder();
        infob.setRequireAuthentication(!getSecurityStore().getGuestUser().isActive());
        for (AuthModule authModule : getSecurityStore().getAuthModules()) {
            if (authModule instanceof SpnegoAuthModule) {
                infob.setSpnego(true);
            }
            if (authModule instanceof OpenIDAuthModule) {
                OpenIDConnectInfo.Builder openidb = OpenIDConnectInfo.newBuilder();
                String clientId = ((OpenIDAuthModule) authModule).getClientId();
                openidb.setClientId(clientId);
                String authorizationEndpoint = ((OpenIDAuthModule) authModule).getAuthorizationEndpoint();
                openidb.setAuthorizationEndpoint(authorizationEndpoint);
                String scope = ((OpenIDAuthModule) authModule).getScope();
                openidb.setScope(scope);

                infob.setOpenid(openidb.build());
            }
        }
        return infob.build();
    }

    private void showLoginForm(HandlerContext ctx, OpenIDAuthenticationRequest request) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("contextPath", ctx.getContextPath());
        vars.put("request", request.getMap());
        ctx.render(HttpResponseStatus.OK, "/auth/templates/authorize.html", vars);
    }

    private void showLoginError(HandlerContext ctx, LoginRequest request, String errorMessage) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("contextPath", ctx.getContextPath());
        vars.put("request", request.getMap());
        if (errorMessage != null) {
            vars.put("errorMessage", errorMessage);
        }
        ctx.render(HttpResponseStatus.OK, "/auth/templates/authorize.html", vars);
    }

    private void redirectWithCode(HandlerContext ctx, AuthenticationInfo info, LoginRequest request) {
        String code = generateUrlSafeCode();
        CODE_CACHE.put(code, info);

        QueryStringEncoder qsEncoder = new QueryStringEncoder(request.getRedirectURI());
        qsEncoder.addParam("code", code);

        String state = request.getState();
        if (state != null) {
            qsEncoder.addParam("state", state);
        }

        log.info("Redirecting to " + qsEncoder.toString());
        ctx.sendRedirect(qsEncoder.toString());
    }

    /**
     * Issues time-limited access tokens based on different grant types. Depending on the type of grant, this endpoint
     * may also issue rotating refresh tokens that can be used on the client to establish user sessions that last longer
     * than a single access token, without the user needing to re-login.
     * 
     * TODO ignore global CORS settings on this endpoint (?). We should not encourage passing password credentials
     * directly from a browser context, unless for official clients.
     */
    private void handleToken(HandlerContext ctx) {
        ctx.requireFormEncoding();
        String grantType = ctx.requireFormParameter("grant_type");

        log.info("Access token request using grant_type '{}'", grantType);
        switch (grantType) {
        case "password":
            handleTokenRequestWithPasswordGrant(ctx);
            break;
        case "authorization_code":
            handleTokenRequestWithAuthorizationCode(ctx);
            break;
        case "refresh_token":
            handleTokenRequestWithRefreshToken(ctx);
            break;
        case "client_credentials":
            handleTokenRequestWithClientCredentials(ctx);
            break;
        default:
            throw new BadRequestException("Unsupported grant_type '" + grantType + "'");
        }
    }

    private void handleTokenRequestWithPasswordGrant(HandlerContext ctx) {
        String username = ctx.requireFormParameter("username");
        String password = ctx.requireFormParameter("password");

        AuthenticationToken token = new UsernamePasswordToken(username, password.toCharArray());
        try {
            AuthenticationInfo authenticationInfo = getSecurityStore().login(token).get();
            UserSession session = createSession(ctx, authenticationInfo);
            String refreshToken = tokenStore.generateRefreshToken(session);
            sendNewAccessToken(ctx, authenticationInfo, refreshToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AuthenticationException || cause instanceof AuthorizationException) {
                log.info("Denying access to '" + username + "': " + cause.getMessage());
                throw new UnauthorizedException();
            } else {
                log.error("Unexpected error while attempting user login", cause);
                throw new InternalServerErrorException(cause);
            }
        }
    }

    private void handleTokenRequestWithAuthorizationCode(HandlerContext ctx) {
        String authcode = ctx.requireFormParameter("code");

        AuthenticationInfo authenticationInfo = CODE_CACHE.getIfPresent(authcode);

        // Maybe it's a code coming from one of the AuthModules
        if (authenticationInfo == null) {
            try {
                authenticationInfo = getSecurityStore()
                        .login(new ThirdPartyAuthorizationCode(authcode)).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AuthenticationException || cause instanceof AuthorizationException) {
                    log.info("Denying access: " + cause.getMessage());
                    throw new UnauthorizedException();
                } else {
                    log.error("Unexpected error while attempting user login", cause);
                    throw new InternalServerErrorException(cause);
                }
            }
        }

        UserSession session = createSession(ctx, authenticationInfo);

        // Don't support refresh on SPNEGO-backed sessions. Yamcs knows only about a SPNEGO ticket and cannot check
        // the lifetime of the client's TGT. Clients are required to be smart and fetch another authorization token
        // using the /auth/spnego route (= alternative refresh).
        String refreshToken = null;
        if (authenticationInfo.getAuthenticator() instanceof SpnegoAuthModule) {
            // We don't know the underlying expiration time. To be reconsidered when
            // OP and RP are split (then spnego occurs only on the OP).
            long lifespan = getSecurityStore().getAccessTokenLifespan();
            session.setLifespan(lifespan);
        } else {
            refreshToken = tokenStore.generateRefreshToken(session);
        }
        sendNewAccessToken(ctx, authenticationInfo, refreshToken);
    }

    private UserSession createSession(HandlerContext ctx, AuthenticationInfo authenticationInfo) {
        String ipAddress = ctx.getOriginalHostAddress();
        String hostname = ctx.getOriginalHostName();
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        SessionManager sessionManager = securityStore.getSessionManager();
        UserSession session = sessionManager.createSession(authenticationInfo, ipAddress, hostname);

        String userAgent = ctx.getHeader(HttpHeaderNames.USER_AGENT);
        if (userAgent != null) {
            session.getClients().add(userAgent);
        }

        return session;
    }

    /**
     * Issues a new access token after verifying the provided refresh token. This will also output a new refresh token,
     * thereby enforcing single use of a refresh token.
     */
    private void handleTokenRequestWithRefreshToken(HandlerContext ctx) {
        String refreshToken = ctx.getFormParameter("refresh_token");
        RefreshResult result = tokenStore.verifyRefreshToken(refreshToken);
        if (result == null) {
            throw new UnauthorizedException("Invalid refresh token");
        } else {
            sendNewAccessToken(ctx, result.session.getAuthenticationInfo(), result.refreshToken);
        }
    }

    private void handleTokenRequestWithClientCredentials(HandlerContext ctx) {
        String clientId = null;
        String clientSecret = null;

        String[] basicAuth = ctx.getBasicCredentials();
        if (basicAuth != null) {
            clientId = basicAuth[0];
            clientSecret = basicAuth[1];
        } else {
            clientId = ctx.getFormParameter("client_id");
            clientSecret = ctx.getFormParameter("client_secret");
        }
        if (clientId == null || clientSecret == null) {
            throw new BadRequestException("Missing client id or secret");
        }

        ApplicationCredentials token = new ApplicationCredentials(clientId, clientSecret);
        token.setBecome(ctx.getFormParameter("become"));

        try {
            AuthenticationInfo authenticationInfo = getSecurityStore().login(token).get();
            sendNewAccessToken(ctx, authenticationInfo, null /* no refresh needed, client secret is sufficient */);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AuthenticationException || cause instanceof AuthorizationException) {
                log.info("Denying access to '" + clientId + "': " + cause.getMessage());
                throw new UnauthorizedException();
            } else {
                log.error("Unexpected error while attempting user login", cause);
                throw new InternalServerErrorException(cause);
            }
        }
    }

    private void sendNewAccessToken(HandlerContext ctx, AuthenticationInfo authenticationInfo, String refreshToken) {
        try {
            User user = getSecurityStore().getUserFromCache(authenticationInfo.getUsername());
            TokenResponse response = generateTokenResponse(user, refreshToken);
            tokenStore.registerAccessToken(response.getAccessToken(), authenticationInfo);
            ctx.sendOK(response);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Generates a short-term access token, accompanied by an optional indeterminate refresh token.
     * <p>
     * The refresh token can be used one single time get a new access token (and optional new refresh token).
     */
    private TokenResponse generateTokenResponse(User user, String refreshToken)
            throws InvalidKeyException, NoSuchAlgorithmException {
        int ttl = getSecurityStore().getAccessTokenLifespan() / 1000; // convert to seconds
        String jwt = JwtHelper.generateHS256Token("Yamcs", user.getName(), YamcsServer.getServer().getSecretKey(), ttl);

        TokenResponse.Builder responseb = TokenResponse.newBuilder();
        responseb.setTokenType("bearer");
        responseb.setAccessToken(jwt);
        responseb.setExpiresIn(ttl);
        responseb.setUser(IamApi.toUserInfo(user, true, getDirectory()));

        if (refreshToken != null) {
            responseb.setRefreshToken(refreshToken);
        }

        return responseb.build();
    }

    private static String generateUrlSafeCode() {
        byte[] bytes = new byte[10];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static SecurityStore getSecurityStore() {
        return YamcsServer.getServer().getSecurityStore();
    }

    private static Directory getDirectory() {
        return getSecurityStore().getDirectory();
    }
}
