package org.yamcs.security;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;

/**
 * Identifies users and service accounts based on authentication information stored in the Yamcs {@link Directory}.
 */
public class DirectoryAuthModule implements AuthModule {

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
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        Directory directory = YamcsServer.getServer().getSecurityStore().getDirectory();
        if (token instanceof UsernamePasswordToken) {
            String username = ((UsernamePasswordToken) token).getPrincipal();
            User user = directory.getUser(username);
            if (user != null && !user.isExternallyManaged() && user.getHash() != null) {
                char[] password = ((UsernamePasswordToken) token).getPassword();
                if (directory.validateUserPassword(username, password)) {
                    return new AuthenticationInfo(this, user.getName());
                } else {
                    throw new AuthenticationException("Password does not match");
                }
            }
        }
        return null;
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        // Don't add anything. The directory itself already takes care of this.
        return new AuthorizationInfo();
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }
}
