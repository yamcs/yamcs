package org.yamcs.security;

/**
 * These tokens have all privileges. Used for making internal requests only.
 * @author nm
 *
 */
public class SystemToken implements AuthenticationToken {

    @Override
    public Object getPrincipal() {
        return "System";
    }

    @Override
    public Object getCredentials() {
        return null;
    }

}
