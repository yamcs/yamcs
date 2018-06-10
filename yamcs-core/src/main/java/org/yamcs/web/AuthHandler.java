package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Web.AccessTokenResponse;
import org.yamcs.protobuf.Web.AuthFlow;
import org.yamcs.protobuf.Web.AuthFlow.Type;
import org.yamcs.protobuf.Web.AuthInfo;
import org.yamcs.security.AuthModule;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationToken;
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
     * Issues time-limited access tokens based on provided password credentials.
     */
    private void handleTokenRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if ("application/x-www-form-urlencoded".equals(req.headers().get("Content-Type"))) {
            User user = null;
            HttpPostRequestDecoder formDecoder = new HttpPostRequestDecoder(req);
            try {
                String grantType = getStringFromForm(formDecoder, "grant_type");
                System.out.println("Attempt to get token using " + grantType);

                if ("password".equals(grantType)) {
                    String username = getStringFromForm(formDecoder, "username");
                    String password = getStringFromForm(formDecoder, "password");
                    System.out.println("username " + username + ", " + password);
                    AuthenticationToken token = new UsernamePasswordToken(username, password.toCharArray());
                    user = SecurityStore.getInstance().login(token).get();
                    // TODO ? } else if ("spnego".equals(grantType)) {
                    // Could maybe move the http handling from SpnegoAuthModule here.
                    // Saves us a roundtrip, an intermediate authorization_code, and moves
                    // http dependency from security layer.
                } else if ("authorization_code".equals(grantType)) {
                    // This code must have been previously granted via an extension path such as /auth/spnego
                    // (which is a special case due to the use of Negotiate).
                    // Currently we only support authorization codes that are managed by an AuthModule (hence the
                    // name 'ThirdParty'. This may need to be revised when we add general support for the /authorize
                    // endpoint.
                    String authcode = getStringFromForm(formDecoder, "code");
                    System.out.println("Attempt token request with code " + authcode);
                    user = SecurityStore.getInstance().login(new ThirdPartyAuthorizationCode(authcode)).get();
                } else {
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST,
                            "Unsupported grant_type '" + grantType + "'");
                    return;
                }
            } catch (ExecutionException e) {
                if (e.getCause() instanceof AuthenticationException) {
                    log.debug(e.getCause().getMessage());
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                    return;
                } else {
                    log.error("Unexpected error while attempting user login", e);
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    return;
                }
            } catch (IOException e) {
                log.error("Unexpected error while attempting user login", e);
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return;
            } finally {
                formDecoder.destroy();
            }

            try {
                int ttl = 500; // in seconds
                String jwt = JwtHelper.generateHS256Token(user, YamcsServer.getSecretKey(), ttl);

                AccessTokenResponse.Builder responseb = AccessTokenResponse.newBuilder();
                responseb.setTokenType("bearer");
                responseb.setAccessToken(jwt);
                responseb.setExpiresIn(ttl);
                responseb.setUser(UserRestHandler.toUserInfo(user, false));

                HttpRequestHandler.getAuthorizationChecker().storeTokenToUserMapping(jwt, user);
                HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, responseb.build(), true);
            } catch (InvalidKeyException | NoSuchAlgorithmException e) {
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
        }
    }

    String getStringFromForm(HttpPostRequestDecoder formDecoder, String attributeName) throws IOException {
        InterfaceHttpData d = formDecoder.getBodyHttpData(attributeName);
        if (d.getHttpDataType() == HttpDataType.Attribute) {
            return ((Attribute) d).getValue();
        }

        return null;
    }
}
