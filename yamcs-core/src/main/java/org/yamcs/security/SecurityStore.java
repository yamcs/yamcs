package org.yamcs.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
import org.yamcs.YamcsServer;
import org.yamcs.api.InitException;
import org.yamcs.api.Spec;
import org.yamcs.api.Spec.OptionType;
import org.yamcs.api.ValidationException;
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

    private boolean authenticationEnabled = false;

    private List<AuthModule> authModules = new ArrayList<>();

    private Map<String, User> users = new HashMap<>();
    private User systemUser;
    private User unauthenticatedUser;

    private ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private AtomicBoolean dirty = new AtomicBoolean();

    public SecurityStore() throws InitException {
        Path dataDir = YamcsServer.getServer().getDataDirectory();
        storageDir = dataDir.resolve(YamcsServer.GLOBAL_INSTANCE);

        YConfiguration config = readConfig();

        authenticationEnabled = config.getBoolean("enabled");
        if (authenticationEnabled) {
            for (YConfiguration moduleConfig : config.getConfigList("authModules")) {
                AuthModule authModule = loadAuthModule(moduleConfig);
                authModules.add(authModule);
            }
        } else {
            YConfiguration userProps = config.getConfig("unauthenticatedUser");
            String username = userProps.getString("username");
            if (username.isEmpty() || username.contains(":")) {
                throw new ConfigurationException("Invalid username '" + username + "' for unauthenticatedUser");
            }

            log.warn("Authentication disabled. All connections use the"
                    + " permissions of user '{}'", username);

            unauthenticatedUser = new User(username);
            unauthenticatedUser.setSuperuser(userProps.getBoolean("superuser"));
            if (userProps.containsKey("privileges")) {
                YConfiguration privilegeConfigs = userProps.getConfig("privileges");
                for (String privilegeName : privilegeConfigs.getKeys()) {
                    List<String> objects = privilegeConfigs.getList(privilegeName);
                    if (privilegeName.equals("System")) {
                        for (String object : objects) {
                            unauthenticatedUser.addSystemPrivilege(new SystemPrivilege(object));
                        }
                    } else {
                        ObjectPrivilegeType type = new ObjectPrivilegeType(privilegeName);
                        for (String object : objects) {
                            unauthenticatedUser.addObjectPrivilege(new ObjectPrivilege(type, object));
                        }
                    }
                }
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

    private AuthModule loadAuthModule(YConfiguration moduleConfig) throws InitException {
        String moduleClass = moduleConfig.getString("class");
        YConfiguration moduleArgs = YConfiguration.emptyConfig();
        if (moduleConfig.containsKey("args")) {
            moduleArgs = moduleConfig.getConfig("args");
        }
        log.info("Loading AuthModule " + moduleClass);
        try {
            AuthModule authModule = YObjectLoader.loadObject(moduleClass);
            Spec spec = authModule.getSpec();
            if (log.isDebugEnabled()) {
                Map<String, Object> unsafeArgs = moduleArgs.getRoot();
                Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                log.debug("Raw args for {}: {}", moduleClass, safeArgs);
            }

            moduleArgs = spec.validate((YConfiguration) moduleArgs);

            if (log.isDebugEnabled()) {
                Map<String, Object> unsafeArgs = moduleArgs.getRoot();
                Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                log.debug("Initializing {} with resolved args: {}", moduleArgs, safeArgs);
            }
            authModule.init(moduleArgs);
            return authModule;
        } catch (ValidationException e) {
            throw new ConfigurationException(e);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load AuthModule", e);
        }
    }

    private YConfiguration readConfig() {
        Spec moduleSpec = new Spec();
        moduleSpec.addOption("class", OptionType.STRING).withRequired(true);
        moduleSpec.addOption("args", OptionType.ANY);

        Spec unauthenticatedUserSpec = new Spec();
        unauthenticatedUserSpec.addOption("username", OptionType.STRING).withDefault("admin");
        unauthenticatedUserSpec.addOption("superuser", OptionType.BOOLEAN).withDefault(true);
        unauthenticatedUserSpec.addOption("privileges", OptionType.ANY);

        Spec spec = new Spec();
        spec.addOption("enabled", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("authModules", OptionType.LIST).withElementType(OptionType.MAP).withSpec(moduleSpec);
        spec.addOption("unauthenticatedUser", OptionType.MAP).withSpec(unauthenticatedUserSpec)
                .withApplySpecDefaults(true);

        spec.when("enabled", true).requireAll("authModules");

        YConfiguration yconf = YConfiguration.emptyConfig();
        if (YConfiguration.isDefined("security")) {
            yconf = YConfiguration.getConfiguration("security");
        }
        try {
            yconf = spec.validate(yconf);
        } catch (ValidationException e) {
            throw new ConfigurationException(e);
        }
        return yconf;
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
     * Returns the unauthenticated user. This is always null if authentication is not enabled
     */
    public User getUnauthenticatedUser() {
        return unauthenticatedUser;
    }

    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }

    /**
     * Attempts to authenticate a user with the given token and adds authorization information.
     */
    public CompletableFuture<User> login(AuthenticationToken token) {
        if (!authenticationEnabled) {
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
