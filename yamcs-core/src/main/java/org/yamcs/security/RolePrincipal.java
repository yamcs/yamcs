package org.yamcs.security;

import java.security.Principal;
import java.util.Objects;

/**
 * Represents a role. This princial may be associated with a particular Subject to augment that Subject with a role
 * entity.
 */
public class RolePrincipal implements Principal {

    private String name;

    public RolePrincipal(String name) {
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof RolePrincipal)) {
            return false;
        }

        RolePrincipal other = (RolePrincipal) obj;
        return this.getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
