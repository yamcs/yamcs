package org.yamcs.security;

import java.security.Principal;
import java.util.Objects;

/**
 * Represents a user. This princial may be associated with a particular Subject to augment that Subject with an
 * additional identity.
 */
public class UserPrincipal implements Principal {

    private String name;

    public UserPrincipal(String name) {
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
        if (obj == null || !(obj instanceof UserPrincipal)) {
            return false;
        }

        UserPrincipal other = (UserPrincipal) obj;
        return this.getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
