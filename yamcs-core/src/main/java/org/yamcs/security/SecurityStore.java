package org.yamcs.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

public class SecurityStore {

    private static final Logger log = LoggerFactory.getLogger(SecurityStore.class);
    private static SecurityStore instance;

    private boolean enabled;
    private String unauthenticatedIdentity;
    private YamcsSecurityManager securityManager;

    // Deprecated support for privileges.yaml
    private int maxNoSessions;
    private AuthModule authModule;

    private SecurityStore() {
        enabled = false;
        unauthenticatedIdentity = "admin";

        if (YConfiguration.isDefined("security")) {
            YConfiguration yconf = YConfiguration.getConfiguration("security");
            loadSecurityConfiguration(yconf);
        } else if (YConfiguration.isDefined("privileges")) {
            log.warn("DEPRECATION WARNING: Migrate to security.yaml instead of privileges.yaml");
            YConfiguration yconf = YConfiguration.getConfiguration("privileges");
            loadLegacySecurityConfiguration(yconf);
        } else {
            log.warn("Privileges disabled, all connections are allowed and have full permissions");
        }
    }

    public static synchronized SecurityStore getInstance() {
        if (instance == null) {
            instance = new SecurityStore();
        }
        return instance;
    }

    private void loadSecurityConfiguration(YConfiguration yconf) {
        maxNoSessions = 10;
        if (yconf.containsKey("maxNoSessions")) {
            maxNoSessions = yconf.getInt("maxNoSessions");
        }

        enabled = yconf.getBoolean("enabled");
        if (enabled) {
            try {
                securityManager = YObjectLoader.loadObject(yconf.getMap("securityManager"));
            } catch (IOException e) {
                throw new ConfigurationException("Could not load security configuration", e);
            }
        } else {
            if (yconf.containsKey("unauthenticatedIdentity")) {
                String name = yconf.getString("unauthenticatedIdentity");
                if (name.isEmpty() || name.contains(":")) {
                    throw new ConfigurationException(
                            "Invalid name '" + name + "' for unauthenticatedIdentity");
                }
                unauthenticatedIdentity = name;
            }
        }
    }

    /**
     * Reads security properties from the privileges.yaml file. Will be removed in a future release. Use security.yaml
     * instead.
     */
    private void loadLegacySecurityConfiguration(YConfiguration yconf) {
        maxNoSessions = 10;
        if (yconf.containsKey("maxNoSessions")) {
            maxNoSessions = yconf.getInt("maxNoSessions");
        }

        enabled = yconf.getBoolean("enabled");
        if (enabled) {
            try {
                authModule = YObjectLoader.loadObject(yconf.getMap("authModule"));
            } catch (IOException e) {
                throw new ConfigurationException("Could not load security configuration", e);
            }
        } else {
            if (yconf.containsKey("defaultUser")) {
                String name = yconf.getString("defaultUser");
                if (name.isEmpty() || name.contains(":")) {
                    throw new ConfigurationException(
                            "Invalid name '" + name + "' for default user");
                }
                unauthenticatedIdentity = name;
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public CompletableFuture<String> authenticate(String username, char[] password) {
        if (securityManager != null) {
            return securityManager.validateUser(username, password);
        } else {
            Map<String, String> m = new HashMap<>();
            m.put(DefaultAuthModule.USERNAME, username);
            m.put(DefaultAuthModule.PASSWORD, password.toString());
            return authModule.authenticate(AuthModule.TYPE_USERPASS, m)
                    .thenApply(AuthenticationToken::getPrincipal);
        }
    }

    /**
     * This configuration should not be in core Yamcs. It's specific to CIS.
     */
    @Deprecated
    public int getMaxNoSessions() {
        return maxNoSessions;
    }
}
