package org.yamcs.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.security.protobuf.AccountCollection;
import org.yamcs.security.protobuf.GroupCollection;
import org.yamcs.yarch.ProtobufDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Stores user, group and application information in the Yamcs database.
 */
public class Directory {

    // Reserve first few ids for potential future use
    // (also not to overlap with system and guest users which are not currently in the directory)
    public static final long ID_START = 5;

    // MIGRATION NOTICE:
    //
    // Users and groups used to be stored in "ProtobufDatabase", but we're slowly phasing that
    // out in favor of tables in the Yamcs DB, now that this has the functionality we need.
    //
    // Current phase: mirror Yamcs DB to ProtobufDB, and read from Yamcs DB.
    //
    // Next planned phases:
    // - remove mirror (it is only done for backwards compatibility), only upgrade on-start.
    // - remove ProtobufDB (long-term, no rush)

    private static final Log log = new Log(Directory.class);
    private static final String ACCOUNT_COLLECTION = "accounts";
    private static final String GROUP_COLLECTION = "groups";
    private static final PasswordHasher hasher = new PBKDF2PasswordHasher();

    // Reserve first few ids for potential future use
    // (also not to overlap with system and guest users which are not currently in the directory)
    @Deprecated
    private AtomicInteger accountIdSequence = new AtomicInteger((int) ID_START);
    @Deprecated
    private AtomicInteger groupIdSequence = new AtomicInteger((int) ID_START);

    @Deprecated
    private Map<String, User> users = new ConcurrentHashMap<>();
    @Deprecated
    private Map<String, ServiceAccount> serviceAccounts = new ConcurrentHashMap<>();
    @Deprecated
    private Map<String, Group> groups = new ConcurrentHashMap<>();

    private Map<String, Role> roles = new ConcurrentHashMap<>();

    private DirectoryDb db;
    private ProtobufDatabase protobufDatabase;

    public Directory() throws InitException {
        try {
            db = new DirectoryDb();
            YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
            protobufDatabase = yarch.getProtobufDatabase();

            if (db.listAccounts().isEmpty()) {
                migrateFromProtobufDatabase();
            }

            // Populate in-memory model required for legacy ProtobufDatabase
            // During this migration also the ID is determined from memory (as opposed to using the DB sequence)
            for (var account : db.listAccounts()) {
                if (account instanceof User) {
                    users.put(account.getName(), (User) account);
                    accountIdSequence.set((int) Math.max(accountIdSequence.get(), account.getId()));
                } else if (account instanceof ServiceAccount) {
                    serviceAccounts.put(account.getName(), (ServiceAccount) account);
                    accountIdSequence.set((int) Math.max(accountIdSequence.get(), account.getId()));
                }
            }
            for (var group : db.listGroups()) {
                groups.put(group.getName(), group);
                groupIdSequence.set((int) Math.max(groupIdSequence.get(), group.getId()));
            }
        } catch (YarchException | IOException e) {
            throw new InitException(e);
        }

        loadRoles();
    }

    /**
     * Users and groups used to be stored in "ProtobufDatabase", but that is being phased out in favor of tables in the
     * Yamcs DB, now that this has the functionality we need.
     *
     * This migration is to be kept around for a long time, to allow people with old Yamcs versions to upgrade.
     */
    private void migrateFromProtobufDatabase() throws IOException {
        var yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        var groupCollection = protobufDatabase.get(GROUP_COLLECTION, GroupCollection.class);
        if (groupCollection != null) {
            var idSequence = yarch.getTable("group").getColumnDefinition("id").getSequence();
            idSequence.reset(groupCollection.getSeq() + 1);

            for (var rec : groupCollection.getRecordsList()) {
                db.addGroup(new Group(rec));
            }
        }

        var accountCollection = protobufDatabase.get(ACCOUNT_COLLECTION, AccountCollection.class);
        if (accountCollection != null) {
            var idSequence = yarch.getTable("account").getColumnDefinition("id").getSequence();
            idSequence.reset(accountCollection.getSeq() + 1);

            for (var rec : accountCollection.getRecordsList()) {
                if (rec.hasUserDetail()) {
                    db.addAccount(new User(rec));
                } else if (rec.hasServiceDetail()) {
                    db.addAccount(new ServiceAccount(rec));
                } else {
                    throw new IllegalStateException("Unexpected account type");
                }
            }
        }
    }

    public synchronized void addUser(User user) throws IOException {
        verifyDirectoryUser(user);
        String username = user.getName();
        if (db.findAccountByName(username) != null) {
            throw new IllegalArgumentException("Name '" + username + "' is already taken");
        }
        if (username.isEmpty() || username.contains(":")) {
            throw new IllegalArgumentException("Invalid username '" + username + "'");
        }
        int id = accountIdSequence.incrementAndGet();
        user.setId(id);
        for (Role role : roles.values()) {
            if (role.isDefaultRole()) {
                user.addRole(role.getName(), false);
            }
        }
        log.info("Saving new user {}", user);
        setUserPrivileges(user);
        users.put(user.getName(), user);
        mirrorToProtobufDatabase();
        db.addAccount(user);
    }

    public synchronized void updateUserProperties(User user) {
        if (user.isBuiltIn()) {
            throw new UnsupportedOperationException();
        }
        setUserPrivileges(user);
        users.put(user.getName(), user);
        mirrorToProtobufDatabase();
        db.updateAccount(user);
    }

    private void setUserPrivileges(User user) {
        user.clearDirectoryPrivileges();
        for (String roleName : user.getRoles()) {
            Role role = getRole(roleName);
            if (role != null) {
                for (SystemPrivilege privilege : role.getSystemPrivileges()) {
                    user.addSystemPrivilege(privilege, false);
                }
                for (ObjectPrivilege privilege : role.getObjectPrivileges()) {
                    user.addObjectPrivilege(privilege, false);
                }
            }
        }
    }

    public synchronized void deleteUser(User user) throws IOException {
        verifyDirectoryUser(user);
        log.info("Removing user {}", user);
        var groups = db.listGroups();
        for (var group : groups) {
            if (group.removeMember(user.getId())) {
                db.updateGroup(group);
            }
        }
        users.remove(user.getName());
        mirrorToProtobufDatabase();
        db.deleteAccount(user);
    }

    public synchronized void addGroup(Group group) {
        String groupName = group.getName();
        if (db.findGroupByName(groupName) != null) {
            throw new IllegalArgumentException("Group '" + groupName + "' already exists");
        }
        int id = groupIdSequence.incrementAndGet();
        group.setId(id);

        log.info("Saving new group {}", group);
        groups.put(group.getName(), group);
        mirrorToProtobufDatabase();
        db.addGroup(group);
    }

    public synchronized void renameGroup(String from, String to) {
        if (db.findGroupByName(to) != null) {
            throw new IllegalArgumentException("Group '" + to + "' already exists");
        }
        var group = db.findGroupByName(from);
        group.setName(to);

        groups.remove(from);
        groups.put(to, group);
        mirrorToProtobufDatabase();
        db.updateGroup(group);
    }

    public synchronized void updateGroupProperties(Group group) {
        groups.put(group.getName(), group);
        mirrorToProtobufDatabase();
        db.updateGroup(group);
    }

    public synchronized void deleteGroup(Group group) {
        groups.remove(group.getName());
        mirrorToProtobufDatabase();
        db.deleteGroup(group);
    }

    /**
     * Creates a new service account. The service account is assumed to represent one application only, for which
     * automatically generated credentials are returned. These may be used to identify as that application, for example
     * to generate access tokens.
     */
    public synchronized ApplicationCredentials addServiceAccount(ServiceAccount service) throws IOException {
        String serviceName = service.getName();
        if (db.findAccountByName(serviceName) != null) {
            throw new IllegalArgumentException("Name '" + serviceName + "' is already taken");
        }
        int id = accountIdSequence.incrementAndGet();
        service.setId(id);

        String applicationId = UUID.randomUUID().toString();
        String applicationSecret = CryptoUtils.generateRandomPassword(10);
        String applicationHash = hasher.createHash(applicationSecret.toCharArray());
        service.setApplicationId(applicationId);
        service.setApplicationHash(applicationHash);

        log.info("Saving new service account {}", service);
        serviceAccounts.put(service.getName(), service);
        mirrorToProtobufDatabase();
        db.addAccount(service);

        return new ApplicationCredentials(applicationId, applicationSecret /* not the hash */);
    }

    public synchronized void deleteServiceAccount(ServiceAccount service) {
        serviceAccounts.remove(service.getName());
        mirrorToProtobufDatabase();
        db.deleteAccount(service);
    }

    public synchronized void updateApplicationProperties(ServiceAccount service) {
        serviceAccounts.put(service.getName(), service);
        mirrorToProtobufDatabase();
        db.updateAccount(service);
    }

    private void verifyDirectoryUser(User user) {
        if (user.isBuiltIn()) {
            throw new IllegalArgumentException("Not a directory user");
        }
    }

    @SuppressWarnings("unchecked")
    private void loadRoles() {
        if (YConfiguration.isDefined("roles")) {
            YConfiguration yconf = YConfiguration.getConfiguration("roles");
            Map<String, Object> roleConfig = yconf.getRoot();
            for (String roleName : roleConfig.keySet()) {
                Role role = new Role(roleName);
                if (!YConfiguration.isNull(roleConfig, roleName)) {
                    Map<String, Object> roleDef = YConfiguration.getMap(roleConfig, roleName);
                    roleDef.forEach((typeString, objects) -> {
                        if (typeString.equals("System")) {
                            for (String name : (List<String>) objects) {
                                role.addSystemPrivilege(new SystemPrivilege(name));
                            }
                        } else if (typeString.equals("default")) {
                            role.setDefaultRole((Boolean) objects);
                        } else {
                            ObjectPrivilegeType type = new ObjectPrivilegeType(typeString);
                            for (String object : (List<String>) objects) {
                                role.addObjectPrivilege(new ObjectPrivilege(type, object));
                            }
                        }
                    });
                }
                roles.put(role.getName(), role);
            }
        }
    }

    /**
     * For backwards compatibility reasons, copy the Yamcs DB in full to the legacy Protobuf Database. This can be
     * removed in a year or so.
     */
    @Deprecated
    private synchronized void mirrorToProtobufDatabase() {
        AccountCollection.Builder accountsb = AccountCollection.newBuilder();
        accountsb.setSeq(accountIdSequence.get());
        for (User user : users.values()) {
            accountsb.addRecords(user.toRecord());
        }
        for (ServiceAccount service : serviceAccounts.values()) {
            accountsb.addRecords(service.toRecord());
        }
        GroupCollection.Builder groupsb = GroupCollection.newBuilder();
        groupsb.setSeq(groupIdSequence.get());
        for (Group group : groups.values()) {
            groupsb.addRecords(group.toRecord());
        }
        try {
            protobufDatabase.save(ACCOUNT_COLLECTION, accountsb.build());
            protobufDatabase.save(GROUP_COLLECTION, groupsb.build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Validates the provided password against the stored password hash of a user.
     * 
     * @return true if the password is correct, false otherwise
     */
    public boolean validateUserPassword(String username, char[] password) {
        var account = db.findAccountByName(username);
        if (account instanceof User && ((User) account).getHash() != null) {
            return hasher.validatePassword(password, ((User) account).getHash());
        } else {
            return false;
        }
    }

    /**
     * Validates the provided password against the stored password hash of an application.
     * <p>
     * Currently this is only functional for service accounts (which map to one and only one application), but some day
     * we may also want to support user applications.
     */
    public boolean validateApplicationPassword(String applicationId, char[] password) {
        var account = getAccountForApplication(applicationId);
        if (account == null) {
            throw new IllegalArgumentException("No such application");
        }

        if (account instanceof ServiceAccount) {
            return hasher.validatePassword(password, ((ServiceAccount) account).getApplicationHash());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void changePassword(User user, char[] password) {
        if (user.isExternallyManaged()) {
            throw new IllegalArgumentException("The identity of this user is not managed by Yamcs");
        }
        if (!validateUserPassword(user.getName(), password)) {
            String hash = hasher.createHash(password);
            user.setHash(hash);
            users.put(user.name, user);
            mirrorToProtobufDatabase();
            db.updateAccount(user);
        }
    }

    public Account getAccount(String name) {
        User user = getUser(name);
        return user != null ? user : getServiceAccount(name);
    }

    public User getUser(long id) {
        var account = db.findAccount(id);
        var userAccount = (account instanceof User) ? (User) account : null;
        if (userAccount != null) {
            setUserPrivileges(userAccount);
        }
        return userAccount;
    }

    public User getUser(String username) {
        var account = db.findAccountByName(username);
        var userAccount = (account instanceof User) ? (User) account : null;
        if (userAccount != null) {
            setUserPrivileges(userAccount);
        }
        return userAccount;
    }

    public List<User> getUsers() {
        return db.listAccounts().stream()
                .filter(account -> account instanceof User)
                .map(account -> (User) account)
                .collect(Collectors.toList());
    }

    public Account getAccountForApplication(String applicationId) {
        return db.findServiceAccountForApplicationId(applicationId);
    }

    public Group getGroup(String name) {
        return db.findGroupByName(name);
    }

    public List<Group> getGroups() {
        return db.listGroups();
    }

    public List<Group> getGroups(User user) {
        return getGroups().stream()
                .filter(g -> g.hasMember(user.getId()))
                .collect(Collectors.toList());
    }

    public ServiceAccount getServiceAccount(String name) {
        var account = db.findAccountByName(name);
        return (account instanceof ServiceAccount)
                ? (ServiceAccount) account
                : null;
    }

    public List<ServiceAccount> getServiceAccounts() {
        return db.listAccounts().stream()
                .filter(account -> account instanceof ServiceAccount)
                .map(account -> (ServiceAccount) account)
                .collect(Collectors.toList());
    }

    public List<Role> getRoles() {
        return new ArrayList<>(roles.values());
    }

    public Role getRole(String name) {
        return roles.get(name);
    }
}
