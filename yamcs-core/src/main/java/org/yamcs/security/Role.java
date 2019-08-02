package org.yamcs.security;

import org.yamcs.security.protobuf.RoleRecord;

public class Role {

    private int id;
    private String name;
    private String description;

    public Role(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Role(RoleRecord record) {
        id = record.getId();
        name = record.getName();
        if (record.hasDescription()) {
            description = record.getDescription();
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RoleRecord toRecord() {
        RoleRecord.Builder b = RoleRecord.newBuilder();
        b.setId(id);
        b.setName(name);
        if (description != null) {
            b.setDescription(description);
        }
        return b.build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Role)) {
            return false;
        }
        Role other = (Role) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
