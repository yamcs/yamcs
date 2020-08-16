package org.yamcs.security;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

/**
 * AuthModule that identifies users based on an HTTP header property. This can be used when Yamcs is well-protected from
 * spoofing attempts and authentication is done on a reverse proxy, like Apache or Nginx.
 */
public class RemoteUserAuthModule implements AuthModule {

    private String usernameHeader;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("header", OptionType.STRING).withDefault("X-REMOTE-USER");
        return spec;
    }

    public String getHeader() {
        return usernameHeader;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        usernameHeader = args.getString("header");
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof RemoteUserToken) {
            String providedHeader = ((RemoteUserToken) token).getHeader();
            if (usernameHeader.equalsIgnoreCase(providedHeader)) {
                return new AuthenticationInfo(this, ((RemoteUserToken) token).getPrincipal());
            }
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
