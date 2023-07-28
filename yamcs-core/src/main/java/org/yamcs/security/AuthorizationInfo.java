package org.yamcs.security;

import java.util.HashSet;
import java.util.Set;

/**
 * Collection of roles, system and/or object privileges.
 */
public class AuthorizationInfo {

    private boolean superuser;
    private Set<String> roles = new HashSet<>();
    private Set<SystemPrivilege> systemPrivileges = new HashSet<>();
    private Set<ObjectPrivilege> objectPrivileges = new HashSet<>();

    public void grantSuperuser() {
        superuser = true;
    }

    public boolean isSuperuser() {
        return superuser;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<SystemPrivilege> getSystemPrivileges() {
        return systemPrivileges;
    }

    public Set<ObjectPrivilege> getObjectPrivileges() {
        return objectPrivileges;
    }

    public void addRole(String role) {
        roles.add(role);
    }

    public void addSystemPrivilege(SystemPrivilege privilege) {
        systemPrivileges.add(privilege);
    }

    public void addObjectPrivilege(ObjectPrivilege privilege) {
        objectPrivileges.add(privilege);
    }
}
