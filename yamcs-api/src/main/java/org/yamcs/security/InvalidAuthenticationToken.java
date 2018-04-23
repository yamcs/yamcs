package org.yamcs.security;

/**
 * Thrown when an AuthenticationToken is not or no longer valid.
 * 
 * @author nm
 *
 */
@SuppressWarnings("serial")
public class InvalidAuthenticationToken extends Exception {

    public InvalidAuthenticationToken(String message) {
        super(message);
    }
}
