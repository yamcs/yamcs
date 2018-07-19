package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Web.AuthFlow;
import org.yamcs.protobuf.Web.AuthFlow.Type;
import org.yamcs.protobuf.Web.AuthInfo;
import org.yamcs.protobuf.Web.TokenResponse;
import org.yamcs.security.AuthModule;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.AuthorizationException;
import org.yamcs.security.CryptoUtils;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SpnegoAuthModule;
import org.yamcs.security.ThirdPartyAuthorizationCode;
import org.yamcs.security.User;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.web.rest.UserRestHandler;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
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

    // Does not store refresh token itself, but only an hmac of an issued token.
    private static final ConcurrentMap<Hmac, User> refreshTokenHmacs = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.uri());
        String path = qsDecoder.path();
        if (path.equals("/auth")) {
            handleAuthInfoRequest(ctx, req);
        } else if (path.equals("/auth/token")) {
            handleTokenRequest(ctx, req);
        } else {
            for (AuthModule authModule : SecurityStore.getInstance().getAuthModules()) {
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
            AuthInfo.Builder responseb = AuthInfo.newBuilder();
            responseb.setRequireAuthentication(SecurityStore.getInstance().isEnabled());
            for (AuthModule authModule : SecurityStore.getInstance().getAuthModules()) {
                if (authModule instanceof SpnegoAuthModule) {
                    responseb.addFlow(AuthFlow.newBuilder().setType(Type.SPNEGO));
                }
            }
            responseb.addFlow(AuthFlow.newBuilder().setType(Type.PASSWORD));
            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, responseb.build(), true);
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Issues time-limited access tokens based on different grant types. Depending on the type of grant, this endpoint
     * may also issue rotating refresh tokens that can be used on the client to establish user sessions that last longer
     * than a single access token, without the user needing to re-login.
     * 
     * TODO ignore global CORS settings on this endpoint (?). We should not encourage passing password credentials
     * directly from a browser context, unless for official clients.
     */
    private void handleTokenRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
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
            User user = SecurityStore.getInstance().login(token).get();
            sendNewAccessToken(ctx, req, user, true);
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
            User user = SecurityStore.getInstance().login(new ThirdPartyAuthorizationCode(authcode)).get();
            sendNewAccessToken(ctx, req, user, true);
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

        // Verify hash
        Hmac hmac = new Hmac(CryptoUtils.calculateHmac(refreshToken, YamcsServer.getSecretKey()));
        User user = refreshTokenHmacs.get(hmac);
        if (user == null) {
            log.info("Invalid refresh token");
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
        } else {
            // Rotate out this token (enforces single use)
            refreshTokenHmacs.remove(hmac);
            sendNewAccessToken(ctx, req, user, true);
        }
    }

    private void sendNewAccessToken(ChannelHandlerContext ctx, FullHttpRequest req, User user,
            boolean withRefreshToken) {
        try {
            TokenResponse response = generateTokenResponse(user, withRefreshToken);
            HttpRequestHandler.getAuthorizationChecker().storeTokenToUserMapping(response.getAccessToken(), user);
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
    private TokenResponse generateTokenResponse(User user, boolean withRefreshToken)
            throws InvalidKeyException, NoSuchAlgorithmException {
        int ttl = 500; // in seconds
        String jwt = JwtHelper.generateHS256Token(user, YamcsServer.getSecretKey(), ttl);

        TokenResponse.Builder responseb = TokenResponse.newBuilder();
        responseb.setTokenType("bearer");
        responseb.setAccessToken(jwt);
        responseb.setExpiresIn(ttl);
        responseb.setUser(UserRestHandler.toUserInfo(user, false));

        if (withRefreshToken) {
            String refreshToken = UUID.randomUUID().toString();
            Hmac hmac = new Hmac(CryptoUtils.calculateHmac(refreshToken, YamcsServer.getSecretKey()));
            refreshTokenHmacs.put(hmac, user);
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

    /**
     * byte[] wrapper that allows value comparison in HashMap
     */
    private static final class Hmac {

        private byte[] hmac;

        Hmac(byte[] hmac) {
            this.hmac = hmac;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Hmac)) {
                return false;
            }
            return Arrays.equals(hmac, ((Hmac) obj).hmac);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hmac);
        }
    }
}
