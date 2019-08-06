package org.yamcs.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.utils.YObjectLoader;

/**
 * Manages the security layer.
 * 
 * Some security properties can be tweaked in security.yaml
 */
public class SecurityStore {

    private static final Log log = new Log(SecurityStore.class);

    /**
     * Baked-in user for system operations. Not manageable through a directory, not available for login.
     */
    private User systemUser;

    /**
     * Baked-in user for guest access.
     */
    private User guestUser;

    /**
     * Stores users and roles in the Yamcs database.
     */
    private Directory directory;

    /**
     * If true, successful login attempts of users that were not previously known by Yamcs (e.g. when authenticating to
     * Yamcs), are by default inactivated.
     */
    private boolean blockUnknownUsers;

    /**
     * Establish the identity of a user (authentication) and can attribute additional user roles (authorization). These
     * are only used during the login process.
     */
    private List<AuthModule> authModules = new ArrayList<>();

    public SecurityStore() throws InitException {
        YConfiguration config;
        try {
            config = readConfig();
        } catch (ValidationException e) {
            throw new InitException(e);
        }

        // Create the system and guest user. These are not stored in the directory,
        // and can not be used to log in directly.
        generateFixedUsers(config);

        directory = new Directory();
        blockUnknownUsers = config.getBoolean("blockUnknownUsers", false);

        if (config.containsKey("authModules")) {
            for (YConfiguration moduleConfig : config.getConfigList("authModules")) {
                AuthModule authModule = loadAuthModule(moduleConfig);
                authModules.add(authModule);
            }
        }

        if (!config.getBoolean("enabled", true) || authModules.isEmpty()) {
            log.info("Enabling guest access");
            guestUser.setActive(true);
        }
    }

    /**
     * Generates the system and the guest user. These users are not manageable via the directory and can not be used to
     * log in directly.
     */
    private void generateFixedUsers(YConfiguration config) {
        systemUser = new User("System", "System", null);
        systemUser.setId(1);
        systemUser.setSuperuser(true);

        YConfiguration guestConfig = config.getConfig("guest");
        String username = guestConfig.getString("username");
        String name = guestConfig.getString("name", username);
        guestUser = new User(username, name, systemUser);
        guestUser.setId(2);
        guestUser.setSuperuser(guestConfig.getBoolean("superuser"));
        guestUser.setActive(false);
        if (guestConfig.containsKey("privileges")) {
            YConfiguration privilegeConfigs = guestConfig.getConfig("privileges");
            for (String privilegeName : privilegeConfigs.getKeys()) {
                List<String> objects = privilegeConfigs.getList(privilegeName);
                if (privilegeName.equals("System")) {
                    for (String object : objects) {
                        guestUser.addSystemPrivilege(new SystemPrivilege(object));
                    }
                } else {
                    ObjectPrivilegeType type = new ObjectPrivilegeType(privilegeName);
                    for (String object : objects) {
                        guestUser.addObjectPrivilege(new ObjectPrivilege(type, object));
                    }
                }
            }
        }
    }

    private AuthModule loadAuthModule(YConfiguration moduleConfig) throws InitException {
        String moduleClass = moduleConfig.getString("class");
        YConfiguration moduleArgs = YConfiguration.emptyConfig();
        if (moduleConfig.containsKey("args")) {
            moduleArgs = moduleConfig.getConfig("args");
        }
        log.debug("Loading AuthModule " + moduleClass);
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
                log.debug("Initializing {} with resolved args: {}", moduleClass, safeArgs);
            }
            authModule.init(moduleArgs);
            return authModule;
        } catch (ValidationException e) {
            throw new InitException(e);
        } catch (IOException e) {
            throw new InitException("Failed to load AuthModule", e);
        }
    }

    private YConfiguration readConfig() throws ValidationException {
        Spec moduleSpec = new Spec();
        moduleSpec.addOption("class", OptionType.STRING).withRequired(true);
        moduleSpec.addOption("args", OptionType.ANY);

        Spec guestSpec = new Spec();
        guestSpec.addOption("username", OptionType.STRING).withDefault("guest");
        guestSpec.addOption("name", OptionType.STRING);
        guestSpec.addOption("superuser", OptionType.BOOLEAN).withDefault(true);
        guestSpec.addOption("privileges", OptionType.ANY);

        Spec spec = new Spec();
        spec.addOption("enabled", OptionType.BOOLEAN).withDeprecationMessage(
                "Remove this argument. If you want to allow guest access, remove security.yaml");
        spec.addOption("blockUnknownUsers", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("authModules", OptionType.LIST).withElementType(OptionType.MAP).withSpec(moduleSpec);
        spec.addOption("guest", OptionType.MAP).withSpec(guestSpec)
                .withAliases("unauthenticatedUser") // Legacy, remove some day
                .withApplySpecDefaults(true);

        YConfiguration yconf = YConfiguration.emptyConfig();
        if (YConfiguration.isDefined("security")) {
            yconf = YConfiguration.getConfiguration("security");
        }
        yconf = spec.validate(yconf);
        return yconf;
    }

    public Directory getDirectory() {
        return directory;
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

    public User getGuestUser() {
        return guestUser;
    }

    /**
     * Performs the login process. Depending on how Yamcs is configured, this may involve reaching out to an external
     * identity provider. If the login attempt is successful, the associated user is imported or resynchronized in the
     * Yamcs internal user database.
     * <p>
     * This method does not return a {@link User} object. Use {@link #getDirectory()}.
     * 
     * @return a future that resolves to the {@link AuthenticationInfo} when the login was successful. This contains the
     *         username as well as any other principals or credentials specific to a custom identity provider.
     */
    public synchronized CompletableFuture<AuthenticationInfo> login(AuthenticationToken token) {
        CompletableFuture<AuthenticationInfo> f = new CompletableFuture<>();

        // 1. Authenticate. Stops on first match.
        AuthenticationInfo authenticationInfo = null;
        for (AuthModule authModule : authModules) {
            try {
                authenticationInfo = authModule.getAuthenticationInfo(token);
                if (authenticationInfo != null) {
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

        if (authenticationInfo == null) {
            log.info("Cannot identify user for token");
            f.completeExceptionally(new AuthenticationException("Cannot identify user for token"));
            return f;
        }

        User user = directory.getUser(authenticationInfo.getUsername());
        if (user == null) {
            User createdBy = systemUser;
            user = new User(authenticationInfo.getUsername(), authenticationInfo.getName(), createdBy);
            user.setEmail(authenticationInfo.getEmail());
            if (!blockUnknownUsers) {
                user.confirm();
            }
            try {
                directory.addUser(user);
            } catch (IOException e) {
                f.completeExceptionally(e);
                return f;
            }
        }

        if (user.getUsername().equals(systemUser.getUsername()) || !user.isActive()) {
            log.warn("Denying access to {}", user);
            f.completeExceptionally(new AuthenticationException("Access denied"));
            return f;
        }

        // 2. Authorize. All modules get the opportunity.
        for (AuthModule authModule : authModules) {
            try {
                AuthorizationInfo authzInfo = authModule.getAuthorizationInfo(authenticationInfo);
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
        try {
            user.updateLoginData();
            directory.updateUserProperties(user);
            f.complete(authenticationInfo);
        } catch (IOException e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        for (AuthModule authModule : authModules) {
            if (authenticationInfo != null && authModule.equals(authenticationInfo.getProvider())) {
                return authModule.verifyValidity(authenticationInfo);
            }
        }
        return true;
    }
}
