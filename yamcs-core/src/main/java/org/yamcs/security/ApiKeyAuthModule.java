package org.yamcs.security;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Identifies logins based on an API key, this should be used only for calling programs.
 * <p>
 * This AuthModule is currently restricted to generating and verifying in-memory API keys.
 */
public class ApiKeyAuthModule extends AbstractHttpRequestAuthModule implements AuthModule {

    private static final String X_API_KEY = "x-api-key";

    @Override
    public Spec getSpec() {
        return new Spec();
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        throw new UnsupportedOperationException(
                getClass() + " is a built-in. Remove it from etc/security.yaml");
    }

    @Override
    public boolean handles(ChannelHandlerContext ctx, HttpRequest request) {
        return request.headers().contains(X_API_KEY);
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(ChannelHandlerContext ctx, HttpRequest request)
            throws AuthenticationException {
        var securityStore = YamcsServer.getServer().getSecurityStore();
        var apiKey = request.headers().get(X_API_KEY);

        var username = securityStore.getUsernameForApiKey(apiKey);
        if (username == null) {
            throw new AuthenticationException("Invalid API key");
        } else if (username.equals(securityStore.getSystemUser().getName())) {
            return new SystemUserAuthenticationInfo(this);
        } else {
            return new AuthenticationInfo(this, username);
        }
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        // Don't add anything
        return new AuthorizationInfo();
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }
}
