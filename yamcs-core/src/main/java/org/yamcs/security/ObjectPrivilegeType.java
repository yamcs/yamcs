package org.yamcs.security;

import java.util.Objects;

/**
 * Type qualifier for grouping object privileges.
 * <p>
 * This is not an enum because of extensibility reasons.
 */
public class ObjectPrivilegeType {

    public static final ObjectPrivilegeType ManageBucket = new ObjectPrivilegeType("ManageBucket");
    public static final ObjectPrivilegeType CommandHistory = new ObjectPrivilegeType("CommandHistory");
    public static final ObjectPrivilegeType Stream = new ObjectPrivilegeType("Stream");
    public static final ObjectPrivilegeType Command = new ObjectPrivilegeType("Command");
    public static final ObjectPrivilegeType ReadAlgorithm = new ObjectPrivilegeType("ReadAlgorithm");
    public static final ObjectPrivilegeType ReadBucket = new ObjectPrivilegeType("ReadBucket");
    public static final ObjectPrivilegeType ReadPacket = new ObjectPrivilegeType("ReadPacket");
    public static final ObjectPrivilegeType ReadParameter = new ObjectPrivilegeType("ReadParameter");
    public static final ObjectPrivilegeType WriteParameter = new ObjectPrivilegeType("WriteParameter");

    private String type;

    public ObjectPrivilegeType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ObjectPrivilegeType) {
            return Objects.equals(type, ((ObjectPrivilegeType) obj).getType());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return type;
    }
}
