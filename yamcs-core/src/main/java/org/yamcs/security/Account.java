package org.yamcs.security;

import java.util.Objects;

import org.yamcs.security.protobuf.AccountRecord;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Tuple;

/**
 * A {@link User} or an {@link ServiceAccount}
 */
public abstract class Account {

    protected long id;
    protected String name;
    protected String displayName;
    protected boolean active; // Inactive users are considered "blocked"
    protected long createdBy; // Id of the user that created this user
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
        if (record.hasDisplayName() && !record.getDisplayName().isEmpty()) {
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

    Account(Tuple tuple) {
        id = tuple.getLongColumn(DirectoryDb.ACCOUNT_CNAME_ID);
        name = tuple.getColumn(DirectoryDb.ACCOUNT_CNAME_NAME);
        displayName = tuple.getColumn(DirectoryDb.ACCOUNT_CNAME_DISPLAY_NAME);
        active = tuple.getBooleanColumn(DirectoryDb.ACCOUNT_CNAME_ACTIVE);
        if (tuple.hasColumn(DirectoryDb.ACCOUNT_CNAME_CREATION_TIME)) {
            creationTime = tuple.getTimestampColumn(DirectoryDb.ACCOUNT_CNAME_CREATION_TIME);
        }
        if (tuple.hasColumn(DirectoryDb.ACCOUNT_CNAME_CONFIRMATION_TIME)) {
            confirmationTime = tuple.getTimestampColumn(DirectoryDb.ACCOUNT_CNAME_CONFIRMATION_TIME);
        }
        if (tuple.hasColumn(DirectoryDb.ACCOUNT_CNAME_LAST_LOGIN_TIME)) {
            lastLoginTime = tuple.getTimestampColumn(DirectoryDb.ACCOUNT_CNAME_LAST_LOGIN_TIME);
        }
    }

    /**
     * True if this is a built-in account (i.e. one that is not stored)
     */
    public boolean isBuiltIn() {
        return "System".equals(name) || "guest".equals(name);
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
        if (displayName == null || displayName.isEmpty()) {
            this.displayName = null;
        } else {
            this.displayName = displayName;
        }
    }

    void setId(int id) {
        this.id = id;
    }

    void updateLoginData() {
        lastLoginTime = TimeEncoding.getWallclockTime();
    }

    public long getCreatedBy() {
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

    public long getId() {
        return id;
    }

    protected AccountRecord.Builder newRecordBuilder() {
        AccountRecord.Builder b = AccountRecord.newBuilder();
        b.setId(Long.valueOf(id).intValue());
        b.setName(name);
        if (displayName != null) {
            b.setDisplayName(displayName);
        }
        b.setActive(active);
        if (createdBy > 0) {
            b.setCreatedBy(Long.valueOf(createdBy).intValue());
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

    protected Tuple toTuple(boolean forUpdate) {
        var tuple = new Tuple();
        if (!forUpdate) {
            if (id > 0) { // Else, rely on autoincrement
                tuple.addColumn(DirectoryDb.ACCOUNT_CNAME_ID, id);
            }
        }
        tuple.addColumn(DirectoryDb.ACCOUNT_CNAME_NAME, name);
        tuple.addColumn(DirectoryDb.ACCOUNT_CNAME_DISPLAY_NAME, displayName);
        tuple.addColumn(DirectoryDb.ACCOUNT_CNAME_ACTIVE, active);
        if (createdBy > 0) {
            tuple.addColumn(DirectoryDb.ACCOUNT_CNAME_CREATED_BY, createdBy);
        } else {
            tuple.addColumn(DirectoryDb.ACCOUNT_CNAME_CREATED_BY, null);
        }
        tuple.addTimestampColumn(DirectoryDb.ACCOUNT_CNAME_CREATION_TIME, creationTime);
        if (confirmationTime != TimeEncoding.INVALID_INSTANT) {
            tuple.addTimestampColumn(DirectoryDb.ACCOUNT_CNAME_CONFIRMATION_TIME, confirmationTime);
        }
        if (lastLoginTime != TimeEncoding.INVALID_INSTANT) {
            tuple.addTimestampColumn(DirectoryDb.ACCOUNT_CNAME_LAST_LOGIN_TIME, lastLoginTime);
        }
        return tuple;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Account)) {
            return false;
        }
        Account other = (Account) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("[name=%s, id=%s]", name, id);
    }
}
