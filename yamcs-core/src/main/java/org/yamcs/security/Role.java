package org.yamcs.security;

import java.util.HashSet;
import java.util.Set;

/**
 * Collection of system and object privileges.
 */
public class Role {

    private String name;
    private String description;
    private Set<SystemPrivilege> systemPrivileges = new HashSet<>();
    private Set<ObjectPrivilege> objectPrivileges = new HashSet<>();
    private boolean defaultRole = false;

    public Role(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void addSystemPrivilege(SystemPrivilege privilege) {
        systemPrivileges.add(privilege);
    }

    public void addObjectPrivilege(ObjectPrivilege privilege) {
        objectPrivileges.add(privilege);
    }

    public Set<SystemPrivilege> getSystemPrivileges() {
        return systemPrivileges;
    }

    public Set<ObjectPrivilege> getObjectPrivileges() {
        return objectPrivileges;
    }

    public void setSystemPrivileges(Set<SystemPrivilege> privileges) {
        systemPrivileges.clear();
        systemPrivileges.addAll(privileges);
    }

    public void setObjectPrivileges(Set<ObjectPrivilege> privileges) {
        objectPrivileges.clear();
        objectPrivileges.addAll(privileges);
    }

    public boolean isDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(boolean defaultRole) {
        this.defaultRole = defaultRole;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Role)) {
            return false;
        }
        Role other = (Role) obj;
        return name == other.name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
