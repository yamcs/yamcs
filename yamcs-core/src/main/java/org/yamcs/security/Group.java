package org.yamcs.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.yamcs.security.protobuf.GroupRecord;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

/**
 * A group is way to manage a set of users.
 */
public class Group {

    private long id;
    private String name;
    private String description;
    private List<Long> members = new ArrayList<>();

    public Group(String name) {
        this.name = name;
    }

    public Group(GroupRecord record) {
        id = record.getId();
        name = record.getName();
        if (record.hasDescription() && !record.getDescription().isEmpty()) {
            description = record.getDescription();
        }
        var memberIds = record.getMembersList().stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        members.addAll(memberIds);
    }

    public Group(Tuple tuple) {
        id = tuple.getLongColumn(DirectoryDb.GROUP_CNAME_ID);
        name = tuple.getColumn(DirectoryDb.GROUP_CNAME_NAME);
        description = tuple.getColumn(DirectoryDb.GROUP_CNAME_DESCRIPTION);
        List<Long> memberIds = tuple.getColumn(DirectoryDb.GROUP_CNAME_MEMBERS);
        if (memberIds != null) {
            members.addAll(memberIds);
        }
    }

    public long getId() {
        return id;
    }

    void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (description == null || description.isEmpty()) {
            this.description = null;
        } else {
            this.description = description;
        }
    }

    public List<Long> getMembers() {
        return new ArrayList<>(members);
    }

    public void addMember(long memberId) {
        this.members.add(memberId);
    }

    public boolean removeMember(long memberId) {
        return members.remove(memberId);
    }

    public boolean hasMember(long memberId) {
        return members.contains(memberId);
    }

    public void setMembers(Set<Long> memberIds) {
        members.clear();
        members.addAll(memberIds);
    }

    public GroupRecord toRecord() {
        GroupRecord.Builder b = GroupRecord.newBuilder();
        b.setId(Long.valueOf(id).intValue());
        b.setName(name);
        b.addAllMembers(members.stream()
                .map(Long::intValue)
                .collect(Collectors.toList()));
        if (description != null) {
            b.setDescription(description);
        }
        return b.build();
    }

    public Tuple toTuple(boolean forUpdate) {
        var tuple = new Tuple();
        if (!forUpdate) {
            if (id > 0) { // Else, rely on autoincrement
                tuple.addColumn(DirectoryDb.GROUP_CNAME_ID, id);
            }
        }
        tuple.addColumn(DirectoryDb.GROUP_CNAME_NAME, name);
        tuple.addColumn(DirectoryDb.GROUP_CNAME_DESCRIPTION, description);
        tuple.addColumn(DirectoryDb.GROUP_CNAME_MEMBERS, DataType.array(DataType.LONG), members);

        return tuple;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Group)) {
            return false;
        }
        Group other = (Group) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
