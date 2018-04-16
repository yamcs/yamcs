package org.yamcs.security;

import java.util.Arrays;
import java.util.Objects;

/**
 * Created by msc on 05/05/15.
 */
public class UsernamePasswordToken implements AuthenticationToken {

    private String username;
    private char[] password;

    public UsernamePasswordToken(final String username, final char[] password) {
        this.username = Objects.requireNonNull(username);
        this.password = password;
    }

    public UsernamePasswordToken(final String username, final String password) {
        this.username = Objects.requireNonNull(username);
        this.password = password != null ? password.toCharArray() : null;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password;
    }

    public String getPasswordS() {
        if (password == null)
            return null;
        return new String(password);
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public void setPassword(String password) {
        this.password = password != null ? password.toCharArray() : null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public String toString() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        UsernamePasswordToken that = (UsernamePasswordToken) o;

        if (!Arrays.equals(password, that.password))
            return false;
        if (username != null ? !username.equals(that.username) : that.username != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + (password != null ? Arrays.hashCode(password) : 0);
        return result;
    }
}
