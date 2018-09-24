package org.yamcs.security;

import java.util.Objects;

/**
 * Created by msc on 05/05/15.
 */
public class UsernamePasswordToken implements AuthenticationToken {

    private String username;
    private char[] password;

    public UsernamePasswordToken(String username, char[] password) {
        this.username = Objects.requireNonNull(username);
        this.password = password;
    }

    @Override
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
