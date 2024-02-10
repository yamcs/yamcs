package org.yamcs.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.TreeSet;

/**
 * Covers a user session. Current assumption is that all such sessions use the refresh flow.
 */
public class UserSession {

    private String id;
    private AuthenticationInfo authenticationInfo;
    private String ipAddress;
    private String hostname;
    private Instant startTime;
    private Instant lastAccessTime;
    private Set<String> clients = new TreeSet<>();
    private long lifespan;

    public UserSession(String id, AuthenticationInfo authenticationInfo,
            String ipAddress, String hostname, long lifespan) {
        this.id = id;
        this.authenticationInfo = authenticationInfo;
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.lifespan = lifespan;
        startTime = Instant.now();
        lastAccessTime = startTime;
    }

    public String getId() {
        return id;
    }

    public String getLogin() {
        return authenticationInfo.getUsername();
    }

    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getLastAccessTime() {
        return lastAccessTime;
    }

    public Set<String> getClients() {
        return clients;
    }

    public void setLifespan(long lifespan) {
        this.lifespan = lifespan;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - lastAccessTime.toEpochMilli() > lifespan;
    }

    public Instant getExpirationTime() {
        return lastAccessTime.plus(lifespan, ChronoUnit.MILLIS);
    }

    void touch() {
        lastAccessTime = Instant.now();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserSession)) {
            return false;
        }
        var other = (UserSession) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s [login=%s]", id, authenticationInfo.getUsername());
    }
}
