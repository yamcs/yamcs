package org.yamcs.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data holder for information related to a verified authentication attempt.
 * <p>
 * The default implementation retains only the verified username, extending classes may add other information such as
 * externally issued tickets.
 */
public class AuthenticationInfo {

    private AuthModule authenticator;
    private String username;
    private String displayName;
    private String email;
    private Map<String, String> externalIdentities = new HashMap<>(2);

    public AuthenticationInfo(AuthModule authenticator, String username) {
        this.authenticator = Objects.requireNonNull(authenticator);
        this.username = Objects.requireNonNull(username);
    }

    /**
     * The {@link AuthModule} that verified this authentication attempt.
     */
    public AuthModule getAuthenticator() {
        return authenticator;
    }

    public boolean isKerberos() {
        return (authenticator instanceof KerberosAuthModule)
                || (authenticator instanceof SpnegoAuthModule);
    }

    /**
     * The username of the user that was verified.
     */
    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Map<String, String> getExternalIdentities() {
        return externalIdentities;
    }

    public void addExternalIdentity(String provider, String externalIdentity) {
        externalIdentities.put(provider, externalIdentity);
    }

    @Override
    public String toString() {
        return username;
    }
}
