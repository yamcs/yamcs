package org.yamcs.security;

import java.util.Objects;

/**
 * A password-based token, usually associated with BASIC AUTH requests (convenient through curl)
 */
public class UsernamePasswordToken implements AuthenticationToken {

    private String username;
    private char[] password;

    public UsernamePasswordToken(String username, char[] password) {
        this.username = Objects.requireNonNull(username);
        this.password = password;
    }

    public String getPrincipal() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return username;
    }
}
