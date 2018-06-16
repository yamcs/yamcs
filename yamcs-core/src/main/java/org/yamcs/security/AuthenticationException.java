package org.yamcs.security;

/**
 * Thrown when an {@link AuthModule} failed to perform the authentication process (backend not available, password does
 * not match, ...).
 */
@SuppressWarnings("serial")
public class AuthenticationException extends Exception {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable t) {
        super(message, t);
    }

    public AuthenticationException(Throwable t) {
        super(t);
    }
}
