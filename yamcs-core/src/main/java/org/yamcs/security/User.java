package org.yamcs.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.security.protobuf.AccountRecord;
import org.yamcs.security.protobuf.ExternalIdentity;
import org.yamcs.security.protobuf.UserAccountRecordDetail;

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
public class User extends Account {

    private String email;
    private String hash; // Password hash, only for internal users

    private boolean superuser;

    private Map<String, String> identitiesByProvider = new HashMap<>();

    private Set<SystemPrivilege> systemPrivileges = new HashSet<>();
    private Map<ObjectPrivilegeType, Set<ObjectPrivilege>> objectPrivileges = new HashMap<>();

    public User(String username, User createdBy) {
        super(username, createdBy);
    }

    User(AccountRecord record) {
        super(record);
        UserAccountRecordDetail userDetail = record.getUserDetail();
        if (userDetail.hasHash()) {
            hash = userDetail.getHash();
        }
        if (userDetail.hasEmail()) {
            email = userDetail.getEmail();
        }
        superuser = userDetail.getSuperuser();
        for (ExternalIdentity identity : userDetail.getIdentitiesList()) {
            identitiesByProvider.put(identity.getProvider(), identity.getIdentity());
        }
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

    public boolean isSuperuser() {
        return superuser;
    }

    public void setSuperuser(boolean superuser) {
        this.superuser = superuser;
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

    AccountRecord toRecord() {
        UserAccountRecordDetail.Builder userDetailb = UserAccountRecordDetail.newBuilder();
        if (hash != null) {
            userDetailb.setHash(hash);
        }
        if (email != null) {
            userDetailb.setEmail(email);
        }
        userDetailb.setSuperuser(superuser);
        identitiesByProvider.forEach((provider, identity) -> {
            userDetailb.addIdentities(ExternalIdentity.newBuilder()
                    .setProvider(provider)
                    .setIdentity(identity));
        });

        return newRecordBuilder().setUserDetail(userDetailb).build();
    }
}
