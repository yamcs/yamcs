package org.yamcs.security;

import org.yamcs.YamcsServer;

/**
 * Special {@link AuthenticationInfo} that can be used by {@link AuthModule}s to identify some access as the System
 * user.
 */
public class SystemUserAuthenticationInfo extends AuthenticationInfo {

    public SystemUserAuthenticationInfo(AuthModule authenticator) {
        super(authenticator, YamcsServer.getServer().getSecurityStore().getSystemUser().getName());
    }

    @Override
    public void setDisplayName(String displayName) {
        throw new UnsupportedOperationException("Protected user");
    }

    @Override
    public void setEmail(String email) {
        throw new UnsupportedOperationException("Protected user");
    }

    @Override
    public void addExternalIdentity(String provider, String externalIdentity) {
        throw new UnsupportedOperationException("Protected user");
    }
}
