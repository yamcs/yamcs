package org.yamcs.http;

import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import java.io.IOException;
import java.net.URLDecoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.http.TokenStore.RefreshResult;
import org.yamcs.http.api.IamApi;
import org.yamcs.protobuf.AuthFlow;
import org.yamcs.protobuf.AuthFlow.Type;
import org.yamcs.protobuf.AuthInfo;
import org.yamcs.protobuf.TokenResponse;
import org.yamcs.security.ApplicationCredentials;
import org.yamcs.security.AuthModule;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.AuthorizationException;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SpnegoAuthModule;
import org.yamcs.security.ThirdPartyAuthorizationCode;
import org.yamcs.security.User;
import org.yamcs.security.UsernamePasswordToken;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;

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
@Sharable
public class AuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    private static SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();

    private TokenStore tokenStore;
    private String contextPath;

    public AuthHandler(TokenStore tokenStore, String contextPath) {
        this.tokenStore = tokenStore;
        this.contextPath = contextPath;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String path = HttpUtils.getPathWithoutContext(req, contextPath);
        if (path.equals("/auth")) {
            handleAuthInfoRequest(ctx, req);
        } else if (path.equals("/auth/token")) {
            handleTokenRequest(ctx, req);
        } else {
            for (AuthModule authModule : securityStore.getAuthModules()) {
                if (authModule instanceof AuthModuleHttpHandler) {
                    AuthModuleHttpHandler httpHandler = (AuthModuleHttpHandler) authModule;
                    if (path.equals("/auth/" + httpHandler.path())) {
                        httpHandler.handle(ctx, req);
                        return;
                    }
                }
            }
            HttpRequestHandler.sendPlainTextError(ctx, req, NOT_FOUND);
        }
    }

    /**
     * Provides general auth information. This path is not secured because it's primary intended use is exactly to
     * determine whether Yamcs is secured or not (e.g. in order to detect if a login screen should be shown to the
     * user).
     */
    private void handleAuthInfoRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (req.method() == HttpMethod.GET) {
            AuthInfo info = createAuthInfo();
            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, info, true);
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, METHOD_NOT_ALLOWED);
        }
    }

    public static AuthInfo createAuthInfo() {
        AuthInfo.Builder infob = AuthInfo.newBuilder();
        infob.setRequireAuthentication(!securityStore.getGuestUser().isActive());
        for (AuthModule authModule : securityStore.getAuthModules()) {
            if (authModule instanceof SpnegoAuthModule) {
                infob.addFlow(AuthFlow.newBuilder().setType(Type.SPNEGO));
            }
        }
        infob.addFlow(AuthFlow.newBuilder().setType(Type.PASSWORD));
        return infob.build();
    }

    /**
     * Issues time-limited access tokens based on different grant types. Depending on the type of grant, this endpoint
     * may also issue rotating refresh tokens that can be used on the client to establish user sessions that last longer
     * than a single access token, without the user needing to re-login.
     * 
     * TODO ignore global CORS settings on this endpoint (?). We should not encourage passing password credentials
     * directly from a browser context, unless for official clients.
     */
    private void handleTokenRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if ("application/x-www-form-urlencoded".equals(req.headers().get("Content-Type"))) {
            HttpPostRequestDecoder formDecoder = new HttpPostRequestDecoder(req);
            try {
                String grantType = getStringFromForm(formDecoder, "grant_type");
                log.info("Access token request using grant_type '{}'", grantType);
                switch (grantType) {
                case "password":
                    handleTokenRequestWithPasswordGrant(ctx, req, formDecoder);
                    break;
                case "authorization_code":
                    handleTokenRequestWithAuthorizationCode(ctx, req, formDecoder);
                    break;
                case "refresh_token":
                    handleTokenRequestWithRefreshToken(ctx, req, formDecoder);
                    break;
                case "client_credentials":
                    handleTokenRequestWithClientCredentials(ctx, req, formDecoder);
                    break;
                case "spnego":
                    // TODO ?
                    // Could maybe move the http handling from SpnegoAuthModule here.
                    // Saves us a roundtrip for the intermediate authorization_code
                    // Spnego with token response is not really covered by oauth spec, and
                    // could be considered a special case due to the browser negotiation.
                default:
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST,
                            "Unsupported grant_type '" + grantType + "'");
                }
            } catch (IOException e) {
                log.error("Unexpected error while attempting user login", e);
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return;
            } finally {
                formDecoder.destroy();
            }
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
        }
    }

    private void handleTokenRequestWithPasswordGrant(ChannelHandlerContext ctx, FullHttpRequest req,
            HttpPostRequestDecoder formDecoder) throws IOException {
        String username = getStringFromForm(formDecoder, "username");
        String password = getStringFromForm(formDecoder, "password");
        AuthenticationToken token = new UsernamePasswordToken(username, password.toCharArray());
        try {
            AuthenticationInfo authenticationInfo = securityStore.login(token).get();
            String refreshToken = tokenStore.generateRefreshToken(authenticationInfo);
            sendNewAccessToken(ctx, req, authenticationInfo, refreshToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AuthenticationException || cause instanceof AuthorizationException) {
                log.info("Denying access to '" + username + "': " + cause.getMessage());
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
            } else {
                log.error("Unexpected error while attempting user login", cause);
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void handleTokenRequestWithAuthorizationCode(ChannelHandlerContext ctx, FullHttpRequest req,
            HttpPostRequestDecoder formDecoder) throws IOException {
        // This code must have been previously granted via an extension path such as /auth/spnego
        // (which is a special case due to the use of Negotiate).
        // Currently we only support authorization codes that are managed by an AuthModule (hence the
        // name 'ThirdParty'. This may need to be revised when we add general support for the /authorize
        // endpoint.
        String authcode = getStringFromForm(formDecoder, "code");
        try {
            AuthenticationInfo authenticationInfo = securityStore.login(new ThirdPartyAuthorizationCode(authcode))
                    .get();
            String refreshToken = tokenStore.generateRefreshToken(authenticationInfo);
            sendNewAccessToken(ctx, req, authenticationInfo, refreshToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AuthenticationException || cause instanceof AuthorizationException) {
                log.info("Denying access: " + cause.getMessage());
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
            } else {
                log.error("Unexpected error while attempting user login", cause);
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * Issues a new access token after verifying the provided refresh token. This will also output a new refresh token,
     * thereby enforcing single use of a refresh token.
     */
    private void handleTokenRequestWithRefreshToken(ChannelHandlerContext ctx, FullHttpRequest req,
            HttpPostRequestDecoder formDecoder) throws IOException {
        String refreshToken = getStringFromForm(formDecoder, "refresh_token");
        RefreshResult result = tokenStore.verifyRefreshToken(refreshToken);
        if (result == null) {
            log.info("Invalid refresh token");
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
        } else {
            sendNewAccessToken(ctx, req, result.authenticationInfo, result.refreshToken);
        }
    }

    private void handleTokenRequestWithClientCredentials(ChannelHandlerContext ctx, FullHttpRequest req,
            HttpPostRequestDecoder formDecoder) throws IOException {
        String clientId = null;
        String clientSecret = null;
        if (req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            String authorizationHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorizationHeader.startsWith("Basic ")) {
                String userpassEncoded = authorizationHeader.substring("Basic ".length());
                String userpassDecoded;
                try {
                    userpassDecoded = new String(Base64.getDecoder().decode(userpassEncoded));
                } catch (IllegalArgumentException e) {
                    log.warn("Could not decode Base64-encoded credentials");
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                String[] parts = userpassDecoded.split(":", 2);
                if (parts.length < 2) {
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                clientId = URLDecoder.decode(parts[0], "UTF-8");
                clientSecret = URLDecoder.decode(parts[1], "UTF-8");
            }
        }
        if (clientId == null) {
            clientId = getStringFromForm(formDecoder, "client_id");
            clientSecret = getStringFromForm(formDecoder, "client_secret");
        }
        if (clientId == null || clientSecret == null) {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        ApplicationCredentials token = new ApplicationCredentials(clientId, clientSecret);
        token.setBecome(getStringFromForm(formDecoder, "become"));

        try {
            AuthenticationInfo authenticationInfo = securityStore.login(token).get();
            sendNewAccessToken(ctx, req, authenticationInfo, null /* no refresh needed, client secret is sufficient */);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AuthenticationException || cause instanceof AuthorizationException) {
                log.info("Denying access to '" + clientId + "': " + cause.getMessage());
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
            } else {
                log.error("Unexpected error while attempting user login", cause);
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void sendNewAccessToken(ChannelHandlerContext ctx, FullHttpRequest req,
            AuthenticationInfo authenticationInfo, String refreshToken) {
        try {
            User user = securityStore.getDirectory().getUser(authenticationInfo.getUsername());
            TokenResponse response = generateTokenResponse(user, refreshToken);
            tokenStore.registerAccessToken(response.getAccessToken(), authenticationInfo);
            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, response, true);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generates a short-term access token, accompanied by an optional indeterminate refresh token.
     * <p>
     * The refresh token can be used one single time get a new access token (and optional new refresh token).
     */
    private TokenResponse generateTokenResponse(User user, String refreshToken)
            throws InvalidKeyException, NoSuchAlgorithmException {
        int ttl = 500; // in seconds
        String jwt = JwtHelper.generateHS256Token(user, YamcsServer.getServer().getSecretKey(), ttl);

        TokenResponse.Builder responseb = TokenResponse.newBuilder();
        responseb.setTokenType("bearer");
        responseb.setAccessToken(jwt);
        responseb.setExpiresIn(ttl);
        responseb.setUser(IamApi.toUserInfo(user, false));

        if (refreshToken != null) {
            responseb.setRefreshToken(refreshToken);
        }

        return responseb.build();
    }

    private String getStringFromForm(HttpPostRequestDecoder formDecoder, String attributeName) throws IOException {
        InterfaceHttpData d = formDecoder.getBodyHttpData(attributeName);
        if (d.getHttpDataType() == HttpDataType.Attribute) {
            return ((Attribute) d).getValue();
        }

        return null;
    }
}
