package org.yamcs.security;

/**
 * Thrown when an {@link AuthModule} failed to perform the authorization process.
 */
@SuppressWarnings("serial")
public class AuthorizationException extends Exception {

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable t) {
        super(message, t);
    }

    public AuthorizationException(Throwable t) {
        super(t);
    }
}
