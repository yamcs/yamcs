package org.yamcs.security;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.security.protobuf.GroupRecord;

/**
 * A group is way to manage a set of users.
 */
public class Group {

    private int id;
    private String name;
    private String description;
    private List<Integer> members = new ArrayList<>();

    public Group(String name) {
        this.name = name;
    }

    public Group(GroupRecord record) {
        id = record.getId();
        name = record.getName();
        if (record.hasDescription()) {
            description = record.getDescription();
        }
        members.addAll(record.getMembersList());
    }

    public int getId() {
        return id;
    }

    void setId(int id) {
        this.id = id;
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

    public List<Integer> getMembers() {
        return new ArrayList<>(members);
    }

    public void addMember(int memberId) {
        this.members.add(memberId);
    }

    public boolean hasMember(int memberId) {
        return members.contains(memberId);
    }

    public GroupRecord toRecord() {
        GroupRecord.Builder b = GroupRecord.newBuilder();
        b.setId(id);
        b.setName(name);
        b.addAllMembers(members);
        if (description != null) {
            b.setDescription(description);
        }
        return b.build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Group)) {
            return false;
        }
        Group other = (Group) obj;
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
