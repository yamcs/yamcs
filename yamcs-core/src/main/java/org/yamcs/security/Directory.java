package org.yamcs.security;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.yamcs.InitException;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.security.protobuf.GroupCollection;
import org.yamcs.security.protobuf.GroupRecord;
import org.yamcs.security.protobuf.UserCollection;
import org.yamcs.security.protobuf.UserRecord;
import org.yamcs.yarch.ProtobufDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Stores user and group information in the Yamcs database.
 */
public class Directory {

    private static final Log log = new Log(Directory.class);
    private static final String USER_COLLECTION = "users";
    private static final String GROUP_COLLECTION = "groups";
    private static final PasswordHasher hasher = new PBKDF2PasswordHasher();

    // Reserve first few ids for potential future use
    // (also not to overlap with system and guest users which are not currently in the directory)
    private AtomicInteger userIdSequence = new AtomicInteger(5);
    private AtomicInteger groupIdSequence = new AtomicInteger(5);

    private Map<String, User> users = new ConcurrentHashMap<>();
    private Map<String, Group> groups = new ConcurrentHashMap<>();

    private ProtobufDatabase protobufDatabase;

    public Directory() throws InitException {
        try {
            YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
            protobufDatabase = yarch.getProtobufDatabase();
            // protobufDatabase.delete(USER_COLLECTION); ///
            UserCollection userCollection = protobufDatabase.get(USER_COLLECTION, UserCollection.class);
            if (userCollection != null) {
                userIdSequence.set(userCollection.getSeq());
                for (UserRecord rec : userCollection.getRecordsList()) {
                    users.put(rec.getUsername(), new User(rec));
                }
            }
            GroupCollection groupCollection = protobufDatabase.get(GROUP_COLLECTION, GroupCollection.class);
            if (groupCollection != null) {
                groupIdSequence.set(groupCollection.getSeq());
                for (GroupRecord rec : groupCollection.getRecordsList()) {
                    groups.put(rec.getName(), new Group(rec));
                }
            }
        } catch (YarchException | IOException e) {
            throw new InitException(e);
        }
    }

    public synchronized void addUser(User user) throws IOException {
        String username = user.getUsername();
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("User '" + username + "' already exists");
        }
        if (username.isEmpty() || username.contains(":")) {
            throw new IllegalArgumentException("Invalid username '" + username + "'");
        }
        int id = userIdSequence.incrementAndGet();
        user.setId(id);
        log.info("Saving new user {}", user);
        users.put(user.getUsername(), user);
        persistChanges();
    }

    public synchronized void updateUserProperties(User user) throws IOException {
        users.put(user.getUsername(), user);
        persistChanges();
    }

    public synchronized void addGroup(Group group) throws IOException {
        String groupName = group.getName();
        if (groups.containsKey(groupName)) {
            throw new IllegalArgumentException("Group '" + groupName + "' already exists");
        }
        int id = groupIdSequence.incrementAndGet();
        group.setId(id);
        log.info("Saving new group {}", group);
        groups.put(group.getName(), group);
        persistChanges();
    }

    public synchronized void updateGroupProperties(Group group) throws IOException {
        groups.put(group.getName(), group);
        persistChanges();
    }

    private synchronized void persistChanges() throws IOException {
        UserCollection.Builder usersb = UserCollection.newBuilder();
        usersb.setSeq(userIdSequence.get());
        for (User user : users.values()) {
            usersb.addRecords(user.toRecord());
        }
        GroupCollection.Builder groupsb = GroupCollection.newBuilder();
        groupsb.setSeq(groupIdSequence.get());
        for (Group group : groups.values()) {
            groupsb.addRecords(group.toRecord());
        }
        protobufDatabase.save(USER_COLLECTION, usersb.build());
        protobufDatabase.save(GROUP_COLLECTION, groupsb.build());
    }

    /**
     * Validates the provided password against the stored password hash of a user.
     */
    public boolean validatePassword(User user, char[] password)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (user.isExternallyManaged()) {
            throw new IllegalArgumentException("The identity of this user is not managed by Yamcs");
        }
        return hasher.validatePassword(password, user.getHash());
    }

    public void changePassword(User user, char[] password)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        if (user.isExternallyManaged()) {
            throw new IllegalArgumentException("The identity of this user is not managed by Yamcs");
        }
        String hash = hasher.createHash(password);
        user.setHash(hash);
        persistChanges();
    }

    public User getUser(int id) {
        return users.values().stream()
                .filter(u -> u.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public List<User> getUsers() {
        return new ArrayList<>(users.values());
    }

    public Group getGroup(String name) {
        return groups.get(name);
    }

    public List<Group> getGroups() {
        return new ArrayList<>(groups.values());
    }

    public List<Group> getGroups(User user) {
        return groups.values().stream()
                .filter(g -> g.hasMember(user.getId()))
                .collect(Collectors.toList());
    }
}
