package org.yamcs.security;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Collection of roles and/or privileges.
 * <p>
 * Roles are essentially containers for other roles and/or privileges, although they may get checked for existence as
 * well.
 */
public class AuthorizationInfo {

    private boolean superuser;

    private Set<String> roles = new HashSet<>();
    private Map<String, Set<String>> privileges = new HashMap<>();

    public void grantSuperuser() {
        superuser = true;
    }

    public boolean isSuperuser() {
        return superuser;
    }

    public void addRole(String role) {
        this.roles.add(role);
    }

    public void addPrivilege(PrivilegeType type, String object) {
        addPrivilege(type.toString(), object);
    }

    public void addPrivilege(String type, String object) {
        Set<String> objectPrivileges = privileges.get(type);
        if (objectPrivileges == null) {
            objectPrivileges = new HashSet<>();
            privileges.put(type, objectPrivileges);
        }
        objectPrivileges.add(object);
    }
}
