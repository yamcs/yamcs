package org.yamcs.security;

/**
 * These tokens have all privileges. Used for making internal requests only.
 * @author nm
 *
 */
public class SystemToken implements AuthenticationToken {

    @Override
    public String getPrincipal() {
        return "System";
    }
}
