package org.yamcs.security;

import java.util.Objects;

/**
 * An object privilege is the right to perform a particular action on an object. The object is assumed to be
 * identifiable by a single string. The object may also be expressed as a regular expression, in which case Yamcs will
 * perform pattern matching when doing authorization checks.
 */
public class ObjectPrivilege {

    private ObjectPrivilegeType type;
    private String object;

    public ObjectPrivilege(ObjectPrivilegeType type, String object) {
        this.type = type;
        this.object = object;
    }

    public ObjectPrivilegeType getType() {
        return type;
    }

    public String getObject() {
        return object;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ObjectPrivilege) {
            ObjectPrivilege other = (ObjectPrivilege) obj;
            return Objects.equals(type, other.type) && Objects.equals(object, other.object);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, object);
    }

    @Override
    public String toString() {
        return type + " " + object;
    }
}
