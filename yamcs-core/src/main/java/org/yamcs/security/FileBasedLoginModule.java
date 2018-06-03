package org.yamcs.security;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.yamcs.YConfiguration;

/**
 * Reads user credentials and/or roles from files <tt>users.yaml</tt> and <tt>roles.yaml</tt>
 */
public class FileBasedLoginModule implements LoginModule {

    // Model
    private Map<String, String> users = new HashMap<>();
    private Map<String, List<String>> roles = new HashMap<>();

    private Subject subject;
    private CallbackHandler callbackHandler;

    // Whether passwords in the users.yaml file are hashed
    private boolean hashPasswords = false;

    // authentication status
    private boolean succeeded = false;
    private boolean commitSucceeded = false;
    private Set<Principal> principals = new HashSet<>();
    private String username;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
            Map<String, ?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;

        hashPasswords = "true".equalsIgnoreCase((String) options.get("hashPasswords"));

        if (YConfiguration.isDefined("users")) {
            YConfiguration yconf = YConfiguration.getConfiguration("users");
            Map<String, Object> userConfig = yconf.getRoot();
            for (String username : userConfig.keySet()) {
                users.put(username, YConfiguration.getString(userConfig, username));
            }
        }
        if (YConfiguration.isDefined("roles")) {
            YConfiguration yconf = YConfiguration.getConfiguration("roles");
            Map<String, Object> roleConfig = yconf.getRoot();
            for (String role : roleConfig.keySet()) {
                roles.put(role, YConfiguration.getList(roleConfig, username));
            }
        }
    }

    @Override
    public boolean login() throws LoginException {
        if (callbackHandler == null) {
            throw new LoginException("Error: no CallbackHandler available " +
                    "to garner authentication information from the user");
        }

        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("Username: ");
        callbacks[1] = new PasswordCallback("Password: ", false);

        try {
            callbackHandler.handle(callbacks);
        } catch (IOException e) {
            throw new LoginException(e.toString());
        } catch (UnsupportedCallbackException e) {
            throw new LoginException("Error: " + e.getCallback().toString() +
                    " not available to garner authentication information from the user");
        }

        username = ((NameCallback) callbacks[0]).getName();

        // Copy password to local var
        char[] tmpPassword = ((PasswordCallback) callbacks[1]).getPassword();
        if (tmpPassword == null) {
            tmpPassword = new char[0];
        }
        char[] actualPassword = new char[tmpPassword.length];
        System.arraycopy(tmpPassword, 0, actualPassword, 0, tmpPassword.length);
        ((PasswordCallback) callbacks[1]).clearPassword();

        String expected = users.get(username);
        if (expected == null) {
            throw new FailedLoginException("User does not exist");
        }

        if (hashPasswords) {
            try {
                if (!PasswordHash.validatePassword(actualPassword, expected)) {
                    succeeded = false;
                    username = null;
                    throw new FailedLoginException("Password does not match");
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                succeeded = false;
                username = null;
                throw new FailedLoginException(e.getMessage());
            }
        } else {
            if (!Arrays.equals(expected.toCharArray(), actualPassword)) {
                succeeded = false;
                username = null;
                throw new FailedLoginException("Password does not match");
            }
        }
        succeeded = true;
        return succeeded;
    }

    @Override
    public boolean commit() throws LoginException {
        if (succeeded == false) {
            return false;
        } else {
            principals.add(new UserPrincipal(username));

            List<String> userRoles = roles.get(username);
            if (userRoles != null) {
                for (String role : userRoles) {
                    principals.add(new RolePrincipal(role));
                }
            }

            subject.getPrincipals().addAll(principals);

            username = null;

            commitSucceeded = true;
            return true;
        }
    }

    @Override
    public boolean abort() throws LoginException {
        if (succeeded == false) {
            return false;
        } else if (succeeded == true && commitSucceeded == false) {
            // login succeeded but overall authentication failed
            succeeded = false;
            username = null;
        } else {
            // overall authentication succeeded and commit succeeded,
            // but someone else's commit failed
            logout();
        }
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        subject.getPrincipals().removeAll(principals);
        principals.clear();
        succeeded = false;
        succeeded = commitSucceeded;
        username = null;
        return true;
    }
}
