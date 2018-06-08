package org.yamcs.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

/**
 * Manages the security layer. It allows logging in users with a pluggable variety of extensions. The Security Store
 * reads its configuration from the security.yaml file. This file may define any number of {@link AuthModule}s that may
 * all participate in the authentication and authorization process.
 */
public class SecurityStore {

    private static final Logger log = LoggerFactory.getLogger(SecurityStore.class);
    private static SecurityStore instance;

    private boolean enabled = false;

    private List<AuthModule> authModules = new ArrayList<>();

    private User systemUser;
    private User unauthenticatedUser;

    private SecurityStore() {
        YConfiguration yconf = YConfiguration.getConfiguration("security");

        enabled = yconf.getBoolean("enabled");
        if (!enabled) {
            log.warn("Security disabled");
            if (yconf.containsKey("unauthenticatedUser")) {
                Map<String, Object> userProps = yconf.getMap("unauthenticatedUser");
                String username = YConfiguration.getString(userProps, "username");
                if (username.isEmpty() || username.contains(":")) {
                    throw new ConfigurationException("Invalid username '" + username + "' for unauthenticatedUser");
                }

                unauthenticatedUser = new User(username);
                unauthenticatedUser.setSuperuser(YConfiguration.getBoolean(userProps, "superuser", false));

                // TODO allow configuring privileges? Probably shouldn't come from an AuthModule because enabled=false
            }
        }

        if (yconf.containsKey("authModules")) {
            for (Map<String, Object> moduleConf : yconf.<Map<String, Object>> getList("authModules")) {
                try {
                    AuthModule authModule = YObjectLoader.loadObject(moduleConf);
                    authModules.add(authModule);
                } catch (IOException e) {
                    throw new ConfigurationException("Failed to load AuthModule", e);
                }
            }
        }

        systemUser = new User("System");
        systemUser.setSuperuser(true);
    }

    public static synchronized SecurityStore getInstance() {
        if (instance == null) {
            instance = new SecurityStore();
        }
        return instance;
    }

    public List<AuthModule> getAuthModules() {
        return authModules;
    }

    /**
     * Returns the system user. This user object is only intended for internal use when actions require a user, yet
     * cannot be linked to an actual user. The System user is granted all privileges.
     */
    public User getSystemUser() {
        return systemUser;
    }

    /**
     * Returns the unauthenticated user. This is always null if security is enabled.
     */
    public User getUnauthenticatedUser() {
        return unauthenticatedUser;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Attempts to authenticate a user with the given token and adds authorization information.
     */
    public CompletableFuture<User> login(AuthenticationToken token) {
        if (!enabled) {
            return CompletableFuture.completedFuture(unauthenticatedUser);
        }

        CompletableFuture<User> f = new CompletableFuture<>();

        // 1. Authenticate. Stops on first match.
        AuthenticationInfo authInfo = null;
        for (AuthModule authModule : authModules) {
            try {
                authInfo = authModule.getAuthenticationInfo(token);
                if (authInfo != null) {
                    log.debug("User successfully authenticated by AuthModule {}", authModule);
                    break;
                } else {
                    log.trace("User does not exist according to AuthModule {}", authModule);
                }
            } catch (AuthenticationException e) {
                log.info("AuthModule {} aborted the login process", authModule.getClass());
                f.completeExceptionally(e);
                return f;
            }
        }

        if (authInfo == null) {
            log.info("User does not exist");
            f.completeExceptionally(new AuthenticationException("User does not exist"));
            return f;
        }

        User user = new User(authInfo);

        // 2. Authorize. All modules get the opportunity.
        for (AuthModule authModule : authModules) {
            AuthorizationInfo authzInfo = authModule.getAuthorizationInfo(authInfo);
            for (SystemPrivilege privilege : authzInfo.getSystemPrivileges()) {
                user.addSystemPrivilege(privilege);
            }
            for (ObjectPrivilege privilege : authzInfo.getObjectPrivileges()) {
                user.addObjectPrivilege(privilege);
            }
        }

        f.complete(user);
        return f;
    }

    public boolean verifyValidity(User user) {
        for (AuthModule authModule : authModules) {
            AuthenticationInfo authInfo = user.getAuthenticationInfo();
            if (authInfo != null && authModule.equals(authInfo.getAuthenticator())) {
                return authModule.verifyValidity(user);
            }
        }
        return true;
    }
}
