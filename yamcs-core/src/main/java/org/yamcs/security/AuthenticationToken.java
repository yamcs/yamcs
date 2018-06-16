package org.yamcs.security;

/**
 * Created by msc on 05/05/15.
 */
public interface AuthenticationToken {
    /**
     * Returns the account identity submitted during the authentication process.
     *
     * <p>
     * Ultimately, the object returned is application specific and can represent any account identity (user id, X.509
     * certificate, etc).
     *
     * @return the account identity submitted during the authentication process.
     */
    String getPrincipal();
}
