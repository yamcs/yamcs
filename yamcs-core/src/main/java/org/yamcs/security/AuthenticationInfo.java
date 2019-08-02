package org.yamcs.security;

import java.util.Objects;

/**
 * Data holder for information related to a verified authentication attempt.
 * <p>
 * The default implementation retains only the verified username, extending classes may add other information such as
 * externally issued tickets.
 */
public class AuthenticationInfo {

    private AuthModule provider;
    private String username;
    private String name;
    private String email;

    public AuthenticationInfo(AuthModule provider, String username) {
        this.provider = Objects.requireNonNull(provider);
        this.username = Objects.requireNonNull(username);
    }

    /**
     * The {@link AuthModule} that verified this authentication attempt.
     */
    public AuthModule getProvider() {
        return provider;
    }

    /**
     * The username of the user that was verified.
     */
    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return username;
    }
}
