package org.yamcs.security;

import java.util.Objects;

import org.yamcs.security.protobuf.AccountRecord;
import org.yamcs.utils.TimeEncoding;

/**
 * A {@link User} or an {@link ServiceAccount}
 */
public abstract class Account {

    protected int id;
    protected String name;
    protected String displayName;
    protected boolean active; // Inactive users are considered "blocked"
    protected int createdBy; // Id of the user that created this user
    protected long creationTime = TimeEncoding.INVALID_INSTANT;
    protected long confirmationTime = TimeEncoding.INVALID_INSTANT;
    protected long lastLoginTime = TimeEncoding.INVALID_INSTANT;

    public Account(String name, Account createdBy) {
        this.name = Objects.requireNonNull(name);
        if (createdBy != null) {
            this.createdBy = createdBy.id;
        }
        creationTime = TimeEncoding.getWallclockTime();
    }

    Account(AccountRecord record) {
        id = record.getId();
        name = record.getName();
        if (record.hasDisplayName()) {
            displayName = record.getDisplayName();
        }
        active = record.getActive();
        if (record.hasCreatedBy()) {
            createdBy = record.getCreatedBy();
        }
        creationTime = TimeEncoding.fromProtobufTimestamp(record.getCreationTime());
        if (record.hasConfirmationTime()) {
            confirmationTime = TimeEncoding.fromProtobufTimestamp(record.getConfirmationTime());
        }
        if (record.hasLastLoginTime()) {
            lastLoginTime = TimeEncoding.fromProtobufTimestamp(record.getLastLoginTime());
        }
    }

    /**
     * Identifying attribute, e.g. a username or an application name.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    void setId(int id) {
        this.id = id;
    }

    void updateLoginData() {
        lastLoginTime = TimeEncoding.getWallclockTime();
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getConfirmationTime() {
        return confirmationTime;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void confirm() {
        active = true;
        confirmationTime = TimeEncoding.getWallclockTime();
    }

    public int getId() {
        return id;
    }

    protected AccountRecord.Builder newRecordBuilder() {
        AccountRecord.Builder b = AccountRecord.newBuilder();
        b.setId(id);
        b.setName(name);
        if (displayName != null) {
            b.setDisplayName(displayName);
        }
        b.setActive(active);
        if (createdBy > 0) {
            b.setCreatedBy(createdBy);
        }
        b.setCreationTime(TimeEncoding.toProtobufTimestamp(creationTime));
        if (confirmationTime != TimeEncoding.INVALID_INSTANT) {
            b.setConfirmationTime(TimeEncoding.toProtobufTimestamp(confirmationTime));
        }
        if (lastLoginTime != TimeEncoding.INVALID_INSTANT) {
            b.setLastLoginTime(TimeEncoding.toProtobufTimestamp(lastLoginTime));
        }
        return b;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Account)) {
            return false;
        }
        Account other = (Account) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("[name=%s, id=%s]", name, id);
    }
}
