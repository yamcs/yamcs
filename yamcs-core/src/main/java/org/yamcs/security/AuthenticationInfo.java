package org.yamcs.security;

import java.util.Objects;

public class AuthenticationInfo {

    private AuthModule authenticator;
    private String principal;

    public AuthenticationInfo(AuthModule authenticator, String principal) {
        this.authenticator = Objects.requireNonNull(authenticator);
        this.principal = Objects.requireNonNull(principal);
    }

    public AuthModule getAuthenticator() {
        return authenticator;
    }

    public String getPrincipal() {
        return principal;
    }

    @Override
    public String toString() {
        return principal;
    }
}
