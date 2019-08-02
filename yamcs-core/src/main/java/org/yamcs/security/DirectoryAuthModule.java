package org.yamcs.security;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;

/**
 * Identifies users stored in the Yamcs {@link Directory}.
 */
public class DirectoryAuthModule implements AuthModule {

    private Directory directory;

    @Override
    public Spec getSpec() {
        return new Spec();
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        directory = YamcsServer.getServer().getSecurityStore().getDirectory();
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            String username = token.getPrincipal();
            User user = directory.getUser(username);
            if (user != null && !user.isExternallyManaged()) {
                char[] password = ((UsernamePasswordToken) token).getPassword();
                try {
                    if (directory.validatePassword(user, password)) {
                        return new AuthenticationInfo(this, username);
                    } else {
                        throw new AuthenticationException("Password does not match");
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    throw new AuthenticationException(e);
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
