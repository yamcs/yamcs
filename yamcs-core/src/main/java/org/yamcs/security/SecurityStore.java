package org.yamcs.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.YObjectLoader;
import org.yaml.snakeyaml.Yaml;

/**
 * Manages the security layer. It allows logging in users with a pluggable variety of extensions. The Security Store
 * reads its configuration from the security.yaml file. This file may define any number of {@link AuthModule}s that may
 * all participate in the authentication and authorization process.
 */
public class SecurityStore {

    private Path storageDir;

    private static final Logger log = LoggerFactory.getLogger(SecurityStore.class);
    private static SecurityStore instance;

    private boolean enabled = false;

    private List<AuthModule> authModules = new ArrayList<>();

    private Map<String, User> users = new HashMap<>();
    private User systemUser;
    private User unauthenticatedUser;

    private ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private AtomicBoolean dirty = new AtomicBoolean();

    @SuppressWarnings("unchecked")
    public SecurityStore() {
        YConfiguration yamcsConf = YConfiguration.getConfiguration("yamcs");
        String dataDir = yamcsConf.getString("dataDir");
        storageDir = Paths.get(dataDir).resolve("_global");

        YConfiguration yconf;
        if (YConfiguration.isDefined("security")) {
            yconf = YConfiguration.getConfiguration("security");
        } else {
            yconf = YConfiguration.emptyConfig();
        }

        enabled = yconf.getBoolean("enabled", false);
        if (enabled) {
            if (yconf.containsKey("authModules")) {
                for (Map<String, Object> moduleConf : yconf.<Map<String, Object>> getList("authModules")) {
                    log.info("Loading AuthModule " + YConfiguration.getString(moduleConf, "class"));
                    try {
                        AuthModule authModule = YObjectLoader.loadObject(moduleConf);
                        authModules.add(authModule);
                    } catch (IOException e) {
                        throw new ConfigurationException("Failed to load AuthModule", e);
                    }
                }
            }
        } else {
            log.warn("Security disabled");
            if (yconf.containsKey("unauthenticatedUser")) {
                YConfiguration userProps = yconf.getConfig("unauthenticatedUser");
                String username = userProps.getString("username");
                if (username.isEmpty() || username.contains(":")) {
                    throw new ConfigurationException("Invalid username '" + username + "' for unauthenticatedUser");
                }

                unauthenticatedUser = new User(username);
                unauthenticatedUser.setSuperuser(userProps.getBoolean("superuser", false));
                if (userProps.containsKey("privileges")) {
                    Map<String, Object> privileges = userProps.getMap("privileges");
                    privileges.forEach((typeString, objects) -> {
                        if (typeString.equals("System")) {
                            for (String name : (List<String>) objects) {
                                unauthenticatedUser.addSystemPrivilege(new SystemPrivilege(name));
                            }
                        } else {
                            ObjectPrivilegeType type = new ObjectPrivilegeType(typeString);
                            for (String object : (List<String>) objects) {
                                unauthenticatedUser.addObjectPrivilege(new ObjectPrivilege(type, object));
                            }
                        }
                    });
                }
            } else {
                unauthenticatedUser = new User("admin");
                unauthenticatedUser.setSuperuser(true);
            }
        }

        systemUser = new User("System");
        systemUser.setSuperuser(true);

        exec.scheduleWithFixedDelay(() -> {
            if (dirty.getAndSet(false)) {
                List<Map<String, Object>> allUsers = new ArrayList<>();
                for (Entry<String, User> entry : users.entrySet()) {
                    allUsers.add(entry.getValue().serialize());
                }

                String dump = new Yaml().dump(allUsers);
                Path usersFile = storageDir.resolve("users.yaml");
                try {
                    FileUtils.writeAtomic(usersFile, dump.getBytes());
                } catch (IOException e) {
                    log.warn("Failed to persist user db", e);
                    dirty.set(true);
                    throw new UncheckedIOException(e);
                }
            }
        }, 5000L, 5000L, TimeUnit.MILLISECONDS);
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public List<User> getUsers() {
        return new ArrayList<>(users.values());
    }

    public List<AuthModule> getAuthModules() {
        return authModules;
    }

    @SuppressWarnings("unchecked")
    public <T extends AuthModule> T getAuthModule(Class<T> clazz) {
        for (AuthModule authModule : authModules) {
            if (authModule.getClass() == clazz) {
                return (T) authModule;
            }
        }
        return null;
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
                    log.debug("User successfully authenticated by {}", authModule.getClass().getName());
                    break;
                } else {
                    log.trace("User does not exist according to {}", authModule.getClass().getName());
                }
            } catch (AuthenticationException e) {
                log.info("{} aborted the login process", authModule.getClass().getName());
                f.completeExceptionally(e);
                return f;
            }
        }

        if (authInfo == null) {
            log.info("Cannot identify user for token");
            f.completeExceptionally(new AuthenticationException("Cannot identify user for token"));
            return f;
        }

        User user = new User(authInfo);

        // 2. Authorize. All modules get the opportunity.
        for (AuthModule authModule : authModules) {
            try {
                AuthorizationInfo authzInfo = authModule.getAuthorizationInfo(authInfo);
                if (authzInfo != null) {
                    user.setSuperuser(authzInfo.isSuperuser());
                    for (SystemPrivilege privilege : authzInfo.getSystemPrivileges()) {
                        user.addSystemPrivilege(privilege);
                    }
                    for (ObjectPrivilege privilege : authzInfo.getObjectPrivileges()) {
                        user.addObjectPrivilege(privilege);
                    }
                }
            } catch (AuthorizationException e) {
                log.info("{} aborted the login process", authModule.getClass().getName());
                f.completeExceptionally(e);
                return f;
            }
        }

        log.info("Successfully logged in user {}", user);
        users.put(user.getUsername(), user);
        dirty.set(true);

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
