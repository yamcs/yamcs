package org.yamcs.security;

import java.util.Objects;

/**
 * A token that represents a username without any credentials, usually associated with remote user authentication on a
 * reverse proxy.
 */
public class RemoteUserToken implements AuthenticationToken {

    private String header;
    private String username;

    public RemoteUserToken(String header, String username) {
        this.header = Objects.requireNonNull(header);
        this.username = Objects.requireNonNull(username);
    }

    public String getHeader() {
        return header;
    }

    public String getPrincipal() {
        return username;
    }

    @Override
    public String toString() {
        return username;
    }
}
