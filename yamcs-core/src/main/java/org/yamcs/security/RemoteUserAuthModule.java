package org.yamcs.security;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * AuthModule that identifies users based on an HTTP header property. This can be used when Yamcs is well-protected from
 * spoofing attempts and authentication is done on a reverse proxy, like Apache or Nginx.
 */
public class RemoteUserAuthModule extends AbstractHttpRequestAuthModule {

    protected static final String OPTION_HEADER = "header";

    private String usernameHeader;

    @Override
    public Spec getSpec() {
        var spec = new Spec();
        spec.addOption(OPTION_HEADER, OptionType.STRING).withDefault("X-REMOTE-USER");
        return spec;
    }

    public String getHeader() {
        return usernameHeader;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        usernameHeader = args.getString(OPTION_HEADER);
    }

    @Override
    public boolean handles(ChannelHandlerContext ctx, HttpRequest request) {
        return request.headers().contains(usernameHeader);
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(
            ChannelHandlerContext ctx, HttpRequest request) throws AuthenticationException {
        var username = request.headers().get(usernameHeader);
        if (username != null) {
            return new AuthenticationInfo(this, username);
        }
        return null;
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        return new AuthorizationInfo();
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }
}
