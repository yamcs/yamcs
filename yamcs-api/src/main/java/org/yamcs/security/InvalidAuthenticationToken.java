package org.yamcs.security;

/**
 * Exception thrown in case an AuthenticationToken is not (anymore) valid.
 * 
 * @author nm
 *
 */
public class InvalidAuthenticationToken extends Exception {
    public InvalidAuthenticationToken(String message) {
        super(message);
    }
}
