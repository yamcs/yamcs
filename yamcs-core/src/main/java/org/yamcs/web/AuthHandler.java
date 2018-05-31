package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Web.AccessTokenResponse;
import org.yamcs.protobuf.Web.AuthInfo;
import org.yamcs.security.AuthModule;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.DefaultAuthModule;
import org.yamcs.security.JWT;
import org.yamcs.security.Privilege;
import org.yamcs.security.Realm;
import org.yamcs.security.User;
import org.yamcs.web.rest.UserRestHandler;

import com.google.gson.JsonObject;

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

    private Realm realm;
    private final String authModulePath;
    final AuthModule authModule;

    public AuthHandler() {
        authModule = Privilege.getInstance().getAuthModule();
        if (authModule instanceof DefaultAuthModule) { // hmmm...
            realm = ((DefaultAuthModule) authModule).getRealm();
        }

        authModulePath = (authModule instanceof AuthModuleHttpHandler)
                ? "/auth/" + ((AuthModuleHttpHandler) authModule).path()
                : null;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.uri());
        String path = qsDecoder.path();
        if (path.equals("/auth")) {
            handleAuthInfoRequest(ctx, req);
        } else if (path.equals("/auth/token")) {
            handleTokenRequest(ctx, req);
        } else if (authModulePath != null && path.equals(authModulePath)) {
            ((AuthModuleHttpHandler) authModule).handle(ctx, req);
        } else {
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
            responseb.setRequireAuthentication(Privilege.getInstance().isEnabled());
            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, responseb.build(), true);
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Issues time-limited access tokens based on provided password credentials.
     */
    private void handleTokenRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        AuthenticationToken authToken = null;
        if ("application/x-www-form-urlencoded".equals(req.headers().get("Content-Type"))) {
            HttpPostRequestDecoder formDecoder = new HttpPostRequestDecoder(req);
            try {
                String grantType = getStringFromForm(formDecoder, "grant_type");

                if ("password".equals(grantType)) {
                    Map<String, String> m = new HashMap<>();
                    m.put(DefaultAuthModule.USERNAME, getStringFromForm(formDecoder, "username"));
                    m.put(DefaultAuthModule.PASSWORD, getStringFromForm(formDecoder, "password"));
                    authToken = Privilege.getInstance().getAuthModule()
                            .authenticate(AuthModule.TYPE_USERPASS, m).get();
                } else if ("authorization_code".equals(grantType)) {
                    String authcode = getStringFromForm(formDecoder, "code");
                    authToken = Privilege.getInstance().getAuthModule()
                            .authenticate(AuthModule.TYPE_CODE, authcode).get();
                } else {
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST,
                            "Unsupported grant_type '" + grantType + "'");
                    return;
                }

            } catch (ExecutionException e) {
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                return;
            } catch (IOException e) {
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return;
            } finally {
                formDecoder.destroy();
            }
        }

        if (authToken == null) {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        if (realm == null || !realm.authenticate(authToken)) {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        User user = Privilege.getInstance().getUser(authToken);
        if (user == null) {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        try {
            int ttl = 500; // in seconds
            String jwt = JWT.generateHS256Token(user, YamcsServer.getSecretKey(), ttl);

            AccessTokenResponse.Builder responseb = AccessTokenResponse.newBuilder();
            responseb.setTokenType("bearer");
            responseb.setAccessToken(jwt);
            responseb.setExpiresIn(ttl);
            responseb.setUser(UserRestHandler.toUserInfo(user, false));
            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, responseb.build(), true);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    String getStringFromForm(HttpPostRequestDecoder formDecoder, String attributeName) throws IOException {
        InterfaceHttpData d = formDecoder.getBodyHttpData("name");
        if (d.getHttpDataType() == HttpDataType.Attribute) {
            return ((Attribute) d).getValue();
        }

        return null;
    }
}
