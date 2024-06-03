package org.yamcs.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
     * Whether authorization is checked.
     */
    private boolean enabled;

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
     * Tracks user sessions.
     */
    private SessionManager sessionManager;

    /**
     * If true, successful login attempts of users that were not previously known by Yamcs (e.g. when authenticating to
     * Yamcs), are by default inactivated.
     */
    private boolean blockUnknownUsers;

    /**
     * The maximum time that an access token can be used. When expired (or better: before being expired), a new token
     * may be requested, typically with a refresh token.
     */
    private int accessTokenLifespan;

    private UserCache userCache = new UserCache();

    /**
     * Establish the identity of a user (authentication) and can attribute additional user roles (authorization). These
     * are only used during the login process.
     */
    private List<AuthModule> authModules = new ArrayList<>();

    private Set<SystemPrivilege> systemPrivileges = new CopyOnWriteArraySet<>();
    private Set<ObjectPrivilegeType> objectPrivilegeTypes = new CopyOnWriteArraySet<>();

    /**
     * In-memory API keys. These are experimental, and used to provide authorization to calling programs.
     */
    private Map<String, String> apiKey2username = new ConcurrentHashMap<>();

    // Perform login procedures from a single thread
    private ExecutorService loginExecutor = Executors.newSingleThreadExecutor();

    public SecurityStore() throws InitException {
        YConfiguration config;
        try {
            config = readConfig();
        } catch (ValidationException e) {
            throw new InitException(e);
        }
        enabled = config.getBoolean("enabled");

        // Create the system and guest user. These are not stored in the directory,
        // and can not be used to log in directly.
        generatePredefinedUsers(config);
        generatePredefinedPrivileges();

        directory = new Directory();
        sessionManager = new SessionManager();
        blockUnknownUsers = config.getBoolean("blockUnknownUsers");
        accessTokenLifespan = config.getInt("accessTokenLifespan");

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

                if (authModule instanceof SessionListener) {
                    sessionManager.addSessionListener((SessionListener) authModule);
                }
            }
        }

        // Add last, so external modules have a chance to redefine the user named 'admin'.
        authModules.add(new DirectoryAuthModule());
        authModules.add(new ApiKeyAuthModule());
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
        guestUser.setActive(!enabled);
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
        systemPrivileges.add(SystemPrivilege.CommandOptions);
        systemPrivileges.add(SystemPrivilege.ControlAccess);
        systemPrivileges.add(SystemPrivilege.ControlActivities);
        systemPrivileges.add(SystemPrivilege.ControlAlarms);
        systemPrivileges.add(SystemPrivilege.ControlArchiving);
        systemPrivileges.add(SystemPrivilege.ControlCommandClearances);
        systemPrivileges.add(SystemPrivilege.ControlCommandQueue);
        systemPrivileges.add(SystemPrivilege.ControlLinks);
        systemPrivileges.add(SystemPrivilege.ControlFileTransfers);
        systemPrivileges.add(SystemPrivilege.ControlProcessor);
        systemPrivileges.add(SystemPrivilege.ControlServices);
        systemPrivileges.add(SystemPrivilege.ControlTimeline);
        systemPrivileges.add(SystemPrivilege.ControlTimeCorrelation);
        systemPrivileges.add(SystemPrivilege.CreateInstances);
        systemPrivileges.add(SystemPrivilege.GetMissionDatabase);
        systemPrivileges.add(SystemPrivilege.ManageAnyBucket);
        systemPrivileges.add(SystemPrivilege.ManageParameterLists);
        systemPrivileges.add(SystemPrivilege.ModifyCommandHistory);
        systemPrivileges.add(SystemPrivilege.ReadActivities);
        systemPrivileges.add(SystemPrivilege.ReadAlarms);
        systemPrivileges.add(SystemPrivilege.ReadCommandHistory);
        systemPrivileges.add(SystemPrivilege.ReadFileTransfers);
        systemPrivileges.add(SystemPrivilege.ReadEvents);
        systemPrivileges.add(SystemPrivilege.ReadLinks);
        systemPrivileges.add(SystemPrivilege.ReadSystemInfo);
        systemPrivileges.add(SystemPrivilege.ReadTables);
        systemPrivileges.add(SystemPrivilege.ReadTimeline);
        systemPrivileges.add(SystemPrivilege.WriteEvents);
        systemPrivileges.add(SystemPrivilege.WriteTables);

        objectPrivilegeTypes.add(ObjectPrivilegeType.Command);
        objectPrivilegeTypes.add(ObjectPrivilegeType.CommandHistory);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ManageBucket);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ReadAlgorithm);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ReadBucket);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ReadPacket);
        objectPrivilegeTypes.add(ObjectPrivilegeType.ReadParameter);
        objectPrivilegeTypes.add(ObjectPrivilegeType.Stream);
        objectPrivilegeTypes.add(ObjectPrivilegeType.WriteParameter);
    }

    /**
     * Returns true if security features are activated.
     */
    public boolean isEnabled() {
        return enabled;
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
        spec.addOption("blockUnknownUsers", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("authModules", OptionType.LIST).withElementType(OptionType.MAP).withSpec(moduleSpec);

        boolean securityConfigured = YConfiguration.isDefined("security");
        spec.addOption("enabled", OptionType.BOOLEAN).withDefault(securityConfigured);
        spec.addOption("guest", OptionType.MAP).withSpec(guestSpec)
                .withApplySpecDefaults(true);
        spec.addOption("accessTokenLifespan", OptionType.INTEGER).withDefault(500_000); // Just over 8 minutes

        YConfiguration yconf = YConfiguration.emptyConfig();
        if (securityConfigured) {
            yconf = YConfiguration.getConfiguration("security");
        }
        yconf = spec.validate(yconf);
        return yconf;
    }

    public Directory getDirectory() {
        return directory;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
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
     * Returns the lifespan of access tokens (in milliseconds)
     */
    public int getAccessTokenLifespan() {
        return accessTokenLifespan;
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
    public CompletableFuture<AuthenticationInfo> login(AuthenticationToken token) {
        CompletableFuture<AuthenticationInfo> f = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
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
                    return;
                } catch (Exception e) {
                    log.info("{} threw an unexpected exception", authModule.getClass().getName());
                    f.completeExceptionally(e);
                    return;
                }
            }

            if (authenticationInfo == null) {
                log.info("Cannot identify account for token");
                f.completeExceptionally(new AuthenticationException("Cannot identify account for token"));
                return;
            }

            // 1.b. Notify all modules of successful login.
            // They may choose to bring some additions to the AuthenticationInfo
            for (AuthModule authModule : authModules) {
                try {
                    authModule.authenticationSucceeded(authenticationInfo);
                } catch (Exception e) {
                    log.info("{} threw an unexpected exception", authModule.getClass().getName());
                    f.completeExceptionally(e);
                    return;
                }
            }

            // Access that can not be tied to a specific user
            if (authenticationInfo instanceof SystemUserAuthenticationInfo) {
                userCache.putUserInCache(systemUser);
                f.complete(authenticationInfo);
                return;
            }

            // Disallow using the username of built-in users
            if (isReservedUsername(authenticationInfo.getUsername())) {
                log.warn("Denying access to {}. Username is reserved.", authenticationInfo.getUsername());
                f.completeExceptionally(new AuthenticationException("Access denied"));
                return;
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
                    return;
                }
            }

            if (!user.isActive()) {
                log.warn("Denying access to {}. Account is not active.", user);
                f.completeExceptionally(new AuthenticationException("Access denied"));
                return;
            }

            // 2. Authorize. All modules get the opportunity.
            for (AuthModule authModule : authModules) {
                try {
                    AuthorizationInfo authzInfo = authModule.getAuthorizationInfo(authenticationInfo);
                    if (authzInfo != null) {
                        if (authzInfo.isSuperuser()) { // Only override directory if 'true'
                            user.setSuperuser(true);
                        }
                        for (var role : authzInfo.getRoles()) {
                            user.addRole(role, true);
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
                    return;
                } catch (Exception e) {
                    log.info("{} threw an unexpected exception", authModule.getClass().getName());
                    f.completeExceptionally(e);
                    return;
                }
            }

            log.info("Successfully logged in {}", user);

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
            try {
                directory.updateUserProperties(user);
                userCache.putUserInCache(user);
                f.complete(authenticationInfo);
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        }, loginExecutor);
        return f;
    }

    public User getUserFromCache(String username) {
        return userCache.getUserFromCache(username);
    }

    private boolean isReservedUsername(String username) {
        if (systemUser.getName().equals(username)) {
            return true;
        } else if (guestUser.getName().equals(username)) {
            return true;
        }
        return false;
    }

    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        for (AuthModule authModule : authModules) {
            if (authenticationInfo != null && authModule.equals(authenticationInfo.getAuthenticator())) {
                return authModule.verifyValidity(authenticationInfo);
            }
        }
        return true;
    }

    public String getUsernameForApiKey(String apiKey) {
        return apiKey2username.get(apiKey);
    }

    public String generateApiKey(String username) {
        var apiKey = UUID.randomUUID().toString();
        apiKey2username.put(apiKey, username);
        return apiKey;
    }

    public void removeApiKey(String apiKey) {
        apiKey2username.remove(apiKey);
    }
}
