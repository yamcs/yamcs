package org.yamcs.security;

import java.util.HashSet;
import java.util.Set;

/**
 * Collection of system and object privileges.
 * <p>
 * AuthModules may choose to organize privilege by roles, but such information is not considered by Yamcs itself, where
 * only permissions count.
 */
public class AuthorizationInfo {

    private boolean superuser;

    private Set<SystemPrivilege> systemPrivileges = new HashSet<>();
    private Set<ObjectPrivilege> objectPrivileges = new HashSet<>();

    public void grantSuperuser() {
        superuser = true;
    }

    public boolean isSuperuser() {
        return superuser;
    }

    public Set<SystemPrivilege> getSystemPrivileges() {
        return systemPrivileges;
    }

    public Set<ObjectPrivilege> getObjectPrivileges() {
        return objectPrivileges;
    }

    public void addSystemPrivilege(SystemPrivilege privilege) {
        systemPrivileges.add(privilege);
    }

    public void addObjectPrivilege(ObjectPrivilege privilege) {
        objectPrivileges.add(privilege);
    }
}
