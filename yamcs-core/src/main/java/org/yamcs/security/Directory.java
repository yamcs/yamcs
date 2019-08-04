package org.yamcs.security;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.InitException;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.security.protobuf.RoleCollection;
import org.yamcs.security.protobuf.RoleRecord;
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
    private static final String ROLE_COLLECTION = "roles";
    private static final PasswordHasher hasher = new PBKDF2PasswordHasher();

    // Reserve first few ids for potential future use
    // (also not to overlap with system and guest users which are not currently in the directory)
    private AtomicInteger userIdSequence = new AtomicInteger(5);
    private AtomicInteger roleIdSequence = new AtomicInteger(5);

    private Map<String, User> users = new ConcurrentHashMap<>();
    private Map<String, Role> roles = new ConcurrentHashMap<>();

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
            RoleCollection roleCollection = protobufDatabase.get(ROLE_COLLECTION, RoleCollection.class);
            if (roleCollection != null) {
                roleIdSequence.set(roleCollection.getSeq());
                for (RoleRecord rec : roleCollection.getRecordsList()) {
                    roles.put(rec.getName(), new Role(rec));
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

    private synchronized void persistChanges() throws IOException {
        UserCollection.Builder usersb = UserCollection.newBuilder();
        usersb.setSeq(userIdSequence.get());
        for (User user : users.values()) {
            usersb.addRecords(user.toRecord());
        }
        RoleCollection.Builder rolesb = RoleCollection.newBuilder();
        rolesb.setSeq(roleIdSequence.get());
        for (Role role : roles.values()) {
            rolesb.addRecords(role.toRecord());
        }
        protobufDatabase.save(USER_COLLECTION, usersb.build());
        protobufDatabase.save(ROLE_COLLECTION, rolesb.build());
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

    public Role getRole(String name) {
        return roles.get(name);
    }

    public List<Role> getRoles() {
        return new ArrayList<>(roles.values());
    }
}
