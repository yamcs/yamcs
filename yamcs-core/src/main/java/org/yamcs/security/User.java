package org.yamcs.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    private String username;

    /**
     * May contain contextual security information of use to a specific AuthModule. (e.g. expiration of an externally
     * issued token)
     */
    private AuthenticationInfo authenticationInfo;

    private boolean superuser = false;

    private Set<SystemPrivilege> systemPrivileges = new HashSet<>();
    private Map<ObjectPrivilegeType, Set<ObjectPrivilege>> objectPrivileges = new HashMap<>();

    public User(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        this.username = authenticationInfo.getPrincipal();
    }

    public User(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    public boolean isSuperuser() {
        return superuser;
    }

    public void setSuperuser(boolean superuser) {
        this.superuser = superuser;
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

    @Override
    public String toString() {
        return username;
    }
}
