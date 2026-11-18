package org.yamcs.security;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.logging.Log;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * AuthModule that identifies users based on an HTTP header property. This can be used when authentication is done on a
 * reverse proxy, like Apache or Nginx.
 * <p>
 * The header is only honored on requests coming from one of the {@code trustedProxies} of the HTTP server.
 */
public class RemoteUserAuthModule extends AbstractHttpRequestAuthModule {

    private static final Log log = new Log(RemoteUserAuthModule.class);

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
        if (!request.headers().contains(usernameHeader)) {
            return false;
        }
        var remoteAddress = ctx.channel().remoteAddress();
        var httpServer = ctx.channel().attr(HttpRequestHandler.CTX_HTTP_SERVER).get();
        if (httpServer == null || !httpServer.isTrustedProxy(remoteAddress)) {
            log.warn("Ignoring {} header from {}, which is not a configured trusted proxy",
                    usernameHeader, remoteAddress);
            return false;
        }
        return true;
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
