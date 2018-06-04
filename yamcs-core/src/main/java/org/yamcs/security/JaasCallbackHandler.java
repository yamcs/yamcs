package org.yamcs.security;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 * A JAAS username/password CallbackHandler.
 */
public class JaasCallbackHandler implements CallbackHandler {

    private String username;
    private char[] password;

    public JaasCallbackHandler(String username, char[] password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof PasswordCallback) {
                PasswordCallback passwordCallback = (PasswordCallback) callback;
                if (password == null) {
                    passwordCallback.setPassword(null);
                } else {
                    passwordCallback.setPassword(password);
                }
            } else if (callback instanceof NameCallback) {
                NameCallback nameCallback = (NameCallback) callback;
                if (username == null) {
                    nameCallback.setName(null);
                } else {
                    nameCallback.setName(username);
                }
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }
}
