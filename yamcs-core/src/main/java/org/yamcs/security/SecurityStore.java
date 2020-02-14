package org.yamcs.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.utils.YObjectLoader;

/**
 * Responsible for Identity and Access Management (IAM).
 * <p>
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
     * Stores users, groups and applications in the Yamcs database.
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

    private Set<SystemPrivilege> systemPrivileges = new CopyOnWriteArraySet<>();
    private Set<ObjectPrivilegeType> objectPrivilegeTypes = new CopyOnWriteArraySet<>();

    public SecurityStore() throws InitException {
        YConfiguration config;
        try {
            config = readConfig();
        } catch (ValidationException e) {
            throw new InitException(e);
        }

        // Create the system and guest user. These are not stored in the directory,
        // and can not be used to log in directly.
        generatePredefinedUsers(config);
        generatePredefinedPrivileges();

        directory = new Directory();
        blockUnknownUsers = config.getBoolean("blockUnknownUsers", false);

        if (directory.getUsers().isEmpty()) {
            try {
                generateDefaultAdminUser();
            } catch (IOException e) {
                throw new InitException("Could not create default admin user", e);
            }
        }

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
    private void generatePredefinedUsers(YConfiguration config) {
        systemUser = new User("System", null);
        systemUser.setId(1);
        systemUser.setDisplayName("System");
        systemUser.setSuperuser(true);

        YConfiguration guestConfig = config.getConfig("guest");
        String username = guestConfig.getString("username");
        guestUser = new User(username, systemUser);
        guestUser.setId(2);
        guestUser.setDisplayName(guestConfig.getString("displayName", username));
        guestUser.setSuperuser(guestConfig.getBoolean("superuser"));
        guestUser.setActive(false);
        if (guestConfig.containsKey("privileges")) {
            YConfiguration privilegeConfigs = guestConfig.getConfig("privileges");
            for (String privilegeName : privilegeConfigs.getKeys()) {
                List<String> objects = privilegeConfigs.getList(privilegeName);
                if (privilegeName.equals("System")) {
                    for (String object : objects) {
                        guestUser.addSystemPrivilege(new SystemPrivilege(object), false);
                    }
                } else {
                    ObjectPrivilegeType type = new ObjectPrivilegeType(privilegeName);
                    for (String object : objects) {
                        guestUser.addObjectPrivilege(new ObjectPrivilege(type, object), false);
                    }
                }
            }
        }
    }

    /**
     * Generate a default admin user. This user is stored in the directory and can be used for log in.
     * 
     * TODO mark password as expired.
     */
    private void generateDefaultAdminUser() throws IOException {
        User adminUser = new User("admin", systemUser);
        adminUser.setDisplayName("Administrator");
        adminUser.setSuperuser(true);
        adminUser.setEmail("admin@example.com");
        adminUser.setActive(true);
        adminUser.confirm();
        directory.addUser(adminUser);
        directory.changePassword(adminUser, "admin".toCharArray());
    }

    private void generatePredefinedPrivileges() {
        systemPrivileges.add(SystemPrivilege.ChangeMissionDatabase);
        systemPrivileges.add(SystemPrivilege.Command);
        systemPrivileges.add(SystemPrivilege.ControlAlarms);
        systemPrivileges.add(SystemPrivilege.ControlArchiving);
        systemPrivileges.add(SystemPrivilege.ControlCommandQueue);
        systemPrivileges.add(SystemPrivilege.ControlLinks);
        systemPrivileges.add(SystemPrivilege.ControlProcessor);
        systemPrivileges.add(SystemPrivilege.ControlServices);
        systemPrivileges.add(SystemPrivilege.CreateInstances);
        systemPrivileges.add(SystemPrivilege.GetMissionDatabase);
        systemPrivileges.add(SystemPrivilege.ManageAnyBucket);
        systemPrivileges.add(SystemPrivilege.ReadCommandHistory);
        systemPrivileges.add(SystemPrivilege.ModifyCommandHistory);
        systemPrivileges.add(SystemPrivilege.ReadEvents);
        systemPrivileges.add(SystemPrivilege.ReadTables);
        systemPrivileges.add(SystemPrivilege.WriteEvents);
        systemPrivileges.add(SystemPrivilege.WriteTables);

        objectPrivilegeTypes.add(ObjectPrivilegeType.Command);
        objectPrivilegeTypes.add(ObjectPrivilegeType.CommandHistory);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ManageBucket);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ReadBucket);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ReadPacket);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ReadParameter);
        objectPrivilegeTypes.add(ObjectPrivilegeType.Stream);
        objectPrivilegeTypes.add(ObjectPrivilegeType.WriteParameter);
    }

    public void addSystemPrivilege(SystemPrivilege privilege) {
        systemPrivileges.add(privilege);
    }

    public void addObjectPrivilegeType(ObjectPrivilegeType privilegeType) {
        objectPrivilegeTypes.add(privilegeType);
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
        guestSpec.addOption("displayName", OptionType.STRING);
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

    public Set<SystemPrivilege> getSystemPrivileges() {
        return systemPrivileges;
    }

    public Set<ObjectPrivilegeType> getObjectPrivilegeTypes() {
        return objectPrivilegeTypes;
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
            log.info("Cannot identify account for token");
            f.completeExceptionally(new AuthenticationException("Cannot identify account for token"));
            return f;
        }

        // 1.b. Notify all modules of successful login.
        // They may choose to bring some additions to the AuthenticationInfo
        for (AuthModule authModule : authModules) {
            authModule.authenticationSucceeded(authenticationInfo);
        }

        User user = directory.getUser(authenticationInfo.getUsername());
        if (user == null) {
            User createdBy = systemUser;
            user = new User(authenticationInfo.getUsername(), createdBy);

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

        if (!user.isActive()) {
            log.warn("Denying access to {}. Account is not active.", user);
            f.completeExceptionally(new AuthenticationException("Access denied"));
            return f;
        }

        // 2. Authorize. All modules get the opportunity.
        for (AuthModule authModule : authModules) {
            try {
                AuthorizationInfo authzInfo = authModule.getAuthorizationInfo(authenticationInfo);
                if (authzInfo != null) {
                    if (authzInfo.isSuperuser()) { // Only override directory if 'true'
                        user.setSuperuser(true);
                    }
                    for (SystemPrivilege privilege : authzInfo.getSystemPrivileges()) {
                        user.addSystemPrivilege(privilege, true);
                    }
                    for (ObjectPrivilege privilege : authzInfo.getObjectPrivileges()) {
                        user.addObjectPrivilege(privilege, true);
                    }
                }
            } catch (AuthorizationException e) {
                log.info("{} aborted the login process", authModule.getClass().getName());
                f.completeExceptionally(e);
                return f;
            }
        }

        log.info("Successfully logged in {}", user);
        try {
            user.updateLoginData();
            if (!authenticationInfo.getExternalIdentities().isEmpty()) {
                authenticationInfo.getExternalIdentities().forEach(user::addIdentity);
                if (authenticationInfo.getDisplayName() != null) {
                    user.setDisplayName(authenticationInfo.getDisplayName());
                }
                if (authenticationInfo.getEmail() != null) {
                    user.setEmail(authenticationInfo.getEmail());
                }
            }
            directory.updateUserProperties(user);
            f.complete(authenticationInfo);
        } catch (IOException e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        for (AuthModule authModule : authModules) {
            if (authenticationInfo != null && authModule.equals(authenticationInfo.getAuthenticator())) {
                return authModule.verifyValidity(authenticationInfo);
            }
        }
        return true;
    }
}
