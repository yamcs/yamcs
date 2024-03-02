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
        } else if (token instanceof ApplicationCredentials) {
            String applicationId = ((ApplicationCredentials) token).getApplicationId();
            String applicationSecret = ((ApplicationCredentials) token).getApplicationSecret();
            String become = ((ApplicationCredentials) token).getBecome();
            Account account = directory.getAccountForApplication(applicationId);
            if (account != null) {
                if (directory.validateApplicationPassword(applicationId, applicationSecret.toCharArray())) {
                    if (become == null) {
                        return new AuthenticationInfo(this, account.getName());
                    } else { // TODO add a role, currently we assume all applications can do 'become'.
                        Account becomeAccount = directory.getAccount(become);
                        if (becomeAccount != null) {
                            return new AuthenticationInfo(this, become);
                        } else {
                            throw new AuthenticationException("Unknown account " + become);
                        }
                    }
                } else {
                    throw new AuthenticationException("Secret does not match");
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
