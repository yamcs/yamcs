package org.yamcs.security;

import java.util.Objects;

/**
 * A system privilege is the right to perform a particular action on any object of a particular type.
 * <p>
 * There is no 'wildcard' that represent all system privileges. For such behaviour you should instead consider granting
 * a user the 'superuser' attribute.
 * <p>
 * This is not an enum because of extensibility reasons.
 */
public class SystemPrivilege {

    public static final SystemPrivilege ControlProcessor = new SystemPrivilege("ControlProcessor");
    public static final SystemPrivilege ModifyCommandHistory = new SystemPrivilege("ModifyCommandHistory");
    public static final SystemPrivilege ControlCommandQueue = new SystemPrivilege("ControlCommandQueue");
    public static final SystemPrivilege Command = new SystemPrivilege("Command");
    public static final SystemPrivilege GetMissionDatabase = new SystemPrivilege("GetMissionDatabase");
    public static final SystemPrivilege ControlArchiving = new SystemPrivilege("ControlArchiving");
    public static final SystemPrivilege ControlLinks = new SystemPrivilege("ControlLinks");
    public static final SystemPrivilege ControlServices = new SystemPrivilege("ControlServices");
    public static final SystemPrivilege CreateBucket = new SystemPrivilege("CreateBucket");
    public static final SystemPrivilege WriteBucket = new SystemPrivilege("WriteBucket");
    public static final SystemPrivilege ReadBucket = new SystemPrivilege("ReadBucket");
    public static final SystemPrivilege ReadEvents = new SystemPrivilege("ReadEvents");
    public static final SystemPrivilege WriteEvents = new SystemPrivilege("WriteEvents");
    public static final SystemPrivilege WriteTables = new SystemPrivilege("WriteTables");
    public static final SystemPrivilege ReadTables = new SystemPrivilege("ReadTables");

    private String name;

    public SystemPrivilege(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SystemPrivilege) {
            return Objects.equals(name, ((SystemPrivilege) obj).getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
