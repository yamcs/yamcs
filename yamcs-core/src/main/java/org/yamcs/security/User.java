package org.yamcs.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.yamcs.security.protobuf.ExternalIdentity;
import org.yamcs.security.protobuf.UserRecord;
import org.yamcs.utils.TimeEncoding;

/**
 * A user contains identifying information and a convenient set of methods to perform access control.
 * <p>
 * Users may be assigned two kinds of different privileges:
 * 
 * <ul>
 * <li>System privileges that grant the user the right to perform an action on any object.
 * <li>Object privileges that grant the user the right to perform an action on a specific object.
 * </ul>
 * 
 * Additionally a special attribute <tt>superuser</tt> may have been granted to a user. Users with this attribute are
 * not subjected to privilege checking (i.e. they are allowed everything, even without being assigned privileges).
 */
public class User {

    private int id;
    private String username;
    private String name;
    private String email;
    private String hash; // Password hash, only for internal users
    private boolean active; // Inactive users are considered "blocked"
    private boolean superuser;
    private int createdBy; // Id of the user that created this user
    private long creationTime = TimeEncoding.INVALID_INSTANT;
    private long confirmationTime = TimeEncoding.INVALID_INSTANT;
    private long lastLoginTime = TimeEncoding.INVALID_INSTANT;

    private Map<String, String> identitiesByProvider = new HashMap<>();

    private Set<SystemPrivilege> systemPrivileges = new HashSet<>();
    private Map<ObjectPrivilegeType, Set<ObjectPrivilege>> objectPrivileges = new HashMap<>();

    public User(String username, User createdBy) {
        this.username = Objects.requireNonNull(username);
        if (createdBy != null) {
            this.createdBy = createdBy.id;
        }
        creationTime = TimeEncoding.getWallclockTime();
    }

    User(UserRecord record) {
        id = record.getId();
        username = record.getUsername();
        if (record.hasHash()) {
            hash = record.getHash();
        }
        if (record.hasName()) {
            name = record.getName();
        }
        if (record.hasEmail()) {
            email = record.getEmail();
        }
        active = record.getActive();
        superuser = record.getSuperuser();
        if (record.hasCreatedBy()) {
            createdBy = record.getCreatedBy();
        }
        creationTime = TimeEncoding.fromProtobufTimestamp(record.getCreationTime());
        if (record.hasConfirmationTime()) {
            confirmationTime = TimeEncoding.fromProtobufTimestamp(record.getConfirmationTime());
        }
        if (record.hasLastLoginTime()) {
            lastLoginTime = TimeEncoding.fromProtobufTimestamp(record.getLastLoginTime());
        }
        for (ExternalIdentity identity : record.getIdentitiesList()) {
            identitiesByProvider.put(identity.getProvider(), identity.getIdentity());
        }
    }

    public void confirm() {
        active = true;
        confirmationTime = TimeEncoding.getWallclockTime();
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getHash() {
        return hash;
    }

    public boolean isExternallyManaged() {
        return !identitiesByProvider.isEmpty();
    }

    public void addIdentity(String provider, String identity) {
        identitiesByProvider.put(provider, identity);
    }

    public Set<Entry<String, String>> getIdentityEntrySet() {
        return identitiesByProvider.entrySet();
    }

    public void deleteIdentity(String provider) {
        identitiesByProvider.remove(provider);
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getConfirmationTime() {
        return confirmationTime;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSuperuser() {
        return superuser;
    }

    public void setSuperuser(boolean superuser) {
        this.superuser = superuser;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Set<SystemPrivilege> getSystemPrivileges() {
        return systemPrivileges;
    }

    public Map<ObjectPrivilegeType, Set<ObjectPrivilege>> getObjectPrivileges() {
        return objectPrivileges;
    }

    public Set<ObjectPrivilege> getObjectPrivileges(ObjectPrivilegeType type) {
        Set<ObjectPrivilege> privilegesForType = objectPrivileges.get(type);
        return privilegesForType != null ? privilegesForType : Collections.emptySet();
    }

    public void addSystemPrivilege(SystemPrivilege systemPrivilege) {
        systemPrivileges.add(systemPrivilege);
    }

    public void addObjectPrivilege(ObjectPrivilege objectPrivilege) {
        Set<ObjectPrivilege> privilegesForType = objectPrivileges.get(objectPrivilege.getType());
        if (privilegesForType == null) {
            privilegesForType = new HashSet<>();
            objectPrivileges.put(objectPrivilege.getType(), privilegesForType);
        }
        privilegesForType.add(objectPrivilege);
    }

    public boolean hasSystemPrivilege(SystemPrivilege systemPrivilege) {
        if (superuser) {
            return true;
        }

        return systemPrivileges.contains(systemPrivilege);
    }

    public boolean hasObjectPrivilege(ObjectPrivilegeType type, String object) {
        if (superuser) {
            return true;
        }

        for (ObjectPrivilege privilege : getObjectPrivileges(type)) {
            if (object.matches(privilege.getObject())) {
                return true;
            }
        }

        return false;
    }

    void setId(int id) {
        this.id = id;
    }

    void updateLoginData() {
        lastLoginTime = TimeEncoding.getWallclockTime();
    }

    UserRecord toRecord() {
        UserRecord.Builder b = UserRecord.newBuilder();
        b.setId(id);
        b.setUsername(username);
        if (hash != null) {
            b.setHash(hash);
        }
        if (name != null) {
            b.setName(name);
        }
        if (email != null) {
            b.setEmail(email);
        }
        b.setActive(active);
        b.setSuperuser(superuser);
        if (createdBy > 0) {
            b.setCreatedBy(createdBy);
        }
        b.setCreationTime(TimeEncoding.toProtobufTimestamp(creationTime));
        if (confirmationTime != TimeEncoding.INVALID_INSTANT) {
            b.setConfirmationTime(TimeEncoding.toProtobufTimestamp(confirmationTime));
        }
        if (lastLoginTime != TimeEncoding.INVALID_INSTANT) {
            b.setLastLoginTime(TimeEncoding.toProtobufTimestamp(lastLoginTime));
        }
        identitiesByProvider.forEach((provider, identity) -> {
            b.addIdentities(ExternalIdentity.newBuilder()
                    .setProvider(provider)
                    .setIdentity(identity));
        });
        return b.build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof User)) {
            return false;
        }
        User other = (User) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("[username=%s, id=%s]", username, id);
    }
}
