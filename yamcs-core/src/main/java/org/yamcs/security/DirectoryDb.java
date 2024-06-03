package org.yamcs.security;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.YamcsServer;
import org.yamcs.security.protobuf.ServiceAccountRecordDetail;
import org.yamcs.security.protobuf.UserAccountRecordDetail;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.query.Query;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Stores users and groups in the Yamcs DB.
 */
public class DirectoryDb {

    private static final String ACCOUNT_TABLE_NAME = "account";
    public static final String ACCOUNT_CNAME_ID = "id";
    public static final String ACCOUNT_CNAME_NAME = "name";
    public static final String ACCOUNT_CNAME_DISPLAY_NAME = "display_name";
    public static final String ACCOUNT_CNAME_ACTIVE = "active";
    public static final String ACCOUNT_CNAME_CREATED_BY = "created_by";
    public static final String ACCOUNT_CNAME_CREATION_TIME = "creation_time";
    public static final String ACCOUNT_CNAME_CONFIRMATION_TIME = "confirmation_time";
    public static final String ACCOUNT_CNAME_LAST_LOGIN_TIME = "last_login_time";
    public static final String ACCOUNT_CNAME_USER_DETAIL = "user_detail";
    public static final String ACCOUNT_CNAME_SERVICE_DETAIL = "service_detail";

    private static final String GROUP_TABLE_NAME = "group";
    public static final String GROUP_CNAME_ID = "id";
    public static final String GROUP_CNAME_NAME = "name";
    public static final String GROUP_CNAME_DESCRIPTION = "description";
    public static final String GROUP_CNAME_MEMBERS = "members";

    private YarchDatabaseInstance ydb;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    public DirectoryDb() throws InitException {
        ydb = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        try {
            if (ydb.getTable(ACCOUNT_TABLE_NAME) == null) {
                var q = Query.createTable(ACCOUNT_TABLE_NAME)
                        .withColumn(ACCOUNT_CNAME_ID, DataType.LONG)
                        .withColumn(ACCOUNT_CNAME_NAME, DataType.STRING)
                        .withColumn(ACCOUNT_CNAME_DISPLAY_NAME, DataType.STRING)
                        .withColumn(ACCOUNT_CNAME_ACTIVE, DataType.BOOLEAN)
                        .withColumn(ACCOUNT_CNAME_CREATED_BY, DataType.LONG)
                        .withColumn(ACCOUNT_CNAME_CREATION_TIME, DataType.TIMESTAMP)
                        .withColumn(ACCOUNT_CNAME_CONFIRMATION_TIME, DataType.TIMESTAMP)
                        .withColumn(ACCOUNT_CNAME_LAST_LOGIN_TIME, DataType.TIMESTAMP)
                        .withColumn(ACCOUNT_CNAME_USER_DETAIL, DataType.protobuf(UserAccountRecordDetail.class))
                        .withColumn(ACCOUNT_CNAME_SERVICE_DETAIL, DataType.protobuf(ServiceAccountRecordDetail.class))
                        .autoIncrement(ACCOUNT_CNAME_ID)
                        .primaryKey(ACCOUNT_CNAME_ID);
                ydb.execute(q.toStatement());

                // Reserve first few ids for potential future use
                // (also not to overlap with system and guest users which are not currently in the directory)
                var idSequence = ydb.getTable(ACCOUNT_TABLE_NAME).getColumnDefinition(ACCOUNT_CNAME_ID).getSequence();
                idSequence.reset(5);
            }

            if (ydb.getTable(GROUP_TABLE_NAME) == null) {
                var q = Query.createTable(GROUP_TABLE_NAME)
                        .withColumn(GROUP_CNAME_ID, DataType.LONG)
                        .withColumn(GROUP_CNAME_NAME, DataType.STRING)
                        .withColumn(GROUP_CNAME_DESCRIPTION, DataType.STRING)
                        .withColumn(GROUP_CNAME_MEMBERS, DataType.array(DataType.LONG))
                        .autoIncrement(GROUP_CNAME_ID)
                        .primaryKey(GROUP_CNAME_ID);
                ydb.execute(q.toStatement());

                // Reserve first few ids for potential future use
                var idSequence = ydb.getTable(GROUP_TABLE_NAME).getColumnDefinition(GROUP_CNAME_ID).getSequence();
                idSequence.reset(5);
            }
        } catch (StreamSqlException | ParseException e) {
            throw new InitException(e);
        }
    }

    public void deleteAccounts() {
        rwlock.writeLock().lock();
        try {
            var stmt = Query.deleteFromTable(ACCOUNT_TABLE_NAME).toStatement();
            ydb.execute(stmt);
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public List<Account> listAccounts() {
        rwlock.readLock().lock();
        try {
            var stmt = Query.selectTable(ACCOUNT_TABLE_NAME).toStatement();
            var result = ydb.execute(stmt);
            var accounts = new ArrayList<Account>();
            result.forEachRemaining(tuple -> {
                if (tuple.hasColumn(ACCOUNT_CNAME_USER_DETAIL)) {
                    accounts.add(new User(tuple));
                } else if (tuple.hasColumn(ACCOUNT_CNAME_SERVICE_DETAIL)) {
                    accounts.add(new ServiceAccount(tuple));
                }
            });
            result.close();
            return accounts;
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public Account findAccount(long id) {
        rwlock.readLock().lock();
        try {
            var stmt = Query.selectTable(ACCOUNT_TABLE_NAME)
                    .where(ACCOUNT_CNAME_ID, id)
                    .toStatement();
            var result = ydb.execute(stmt);
            var accounts = new ArrayList<Account>();
            result.forEachRemaining(tuple -> {
                if (tuple.hasColumn(ACCOUNT_CNAME_USER_DETAIL)) {
                    accounts.add(new User(tuple));
                } else if (tuple.hasColumn(ACCOUNT_CNAME_SERVICE_DETAIL)) {
                    accounts.add(new ServiceAccount(tuple));
                }
            });
            result.close();
            if (accounts.size() == 1) {
                return accounts.get(0);
            } else if (accounts.size() > 1) {
                throw new RuntimeException("Too many results");
            } else {
                return null;
            }
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public Account findAccountByName(String name) {
        rwlock.readLock().lock();
        try {
            var stmt = Query.selectTable(ACCOUNT_TABLE_NAME)
                    .where(ACCOUNT_CNAME_NAME, name)
                    .toStatement();
            var result = ydb.execute(stmt);
            var accounts = new ArrayList<Account>();
            result.forEachRemaining(tuple -> {
                if (tuple.hasColumn(ACCOUNT_CNAME_USER_DETAIL)) {
                    accounts.add(new User(tuple));
                } else if (tuple.hasColumn(ACCOUNT_CNAME_SERVICE_DETAIL)) {
                    accounts.add(new ServiceAccount(tuple));
                }
            });
            result.close();
            if (accounts.size() == 1) {
                return accounts.get(0);
            } else if (accounts.size() > 1) {
                throw new RuntimeException("Too many results");
            } else {
                return null;
            }
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public ServiceAccount findServiceAccountForApplicationId(String applicationId) {
        rwlock.readLock().lock();
        try {
            var stmt = Query.selectTable(ACCOUNT_TABLE_NAME)
                    .where(ACCOUNT_CNAME_SERVICE_DETAIL + ".applicationId", applicationId)
                    .toStatement();
            var result = ydb.execute(stmt);
            var accounts = new ArrayList<ServiceAccount>();
            result.forEachRemaining(tuple -> accounts.add(new ServiceAccount(tuple)));
            result.close();
            if (accounts.size() == 1) {
                return accounts.get(0);
            } else if (accounts.size() > 1) {
                throw new RuntimeException("Too many results");
            } else {
                return null;
            }
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void addAccount(Account account) {
        rwlock.writeLock().lock();
        try {
            var tuple = account.toTuple(false /* not forUpdate */);
            var q = Query.insertIntoTable(ACCOUNT_TABLE_NAME, tuple);
            ydb.execute(q.toStatement());
        } catch (StreamSqlException | ParseException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void updateAccount(Account account) {
        rwlock.writeLock().lock();
        try {
            var tuple = account.toTuple(true /* forUpdate */);
            var q = Query.updateTable(ACCOUNT_TABLE_NAME)
                    .set(tuple)
                    .where(ACCOUNT_CNAME_ID, account.getId());
            ydb.execute(q.toStatement());
        } catch (StreamSqlException | ParseException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void deleteAccount(Account account) {
        rwlock.writeLock().lock();
        try {
            var q = Query.deleteFromTable(ACCOUNT_TABLE_NAME)
                    .where(ACCOUNT_CNAME_ID, account.getId());
            ydb.execute(q.toStatement());
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void deleteGroups() {
        rwlock.writeLock().lock();
        try {
            var stmt = Query.deleteFromTable(GROUP_TABLE_NAME).toStatement();
            ydb.execute(stmt);
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public List<Group> listGroups() {
        rwlock.readLock().lock();
        try {
            var stmt = Query.selectTable(GROUP_TABLE_NAME).toStatement();
            var result = ydb.execute(stmt);
            var groups = new ArrayList<Group>();
            result.forEachRemaining(tuple -> groups.add(new Group(tuple)));
            result.close();
            return groups;
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public Group findGroupByName(String name) {
        rwlock.readLock().lock();
        try {
            var stmt = Query.selectTable(GROUP_TABLE_NAME)
                    .where(GROUP_CNAME_NAME, name)
                    .toStatement();
            var result = ydb.execute(stmt);
            var groups = new ArrayList<Group>();
            result.forEachRemaining(tuple -> groups.add(new Group(tuple)));
            result.close();
            if (groups.size() == 1) {
                return groups.get(0);
            } else if (groups.size() > 1) {
                throw new RuntimeException("Too many results");
            } else {
                return null;
            }
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void addGroup(Group group) {
        rwlock.writeLock().lock();
        try {
            var tuple = group.toTuple(false /* not forUpdate */);
            var q = Query.insertIntoTable(GROUP_TABLE_NAME, tuple);
            ydb.execute(q.toStatement());
        } catch (StreamSqlException | ParseException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void updateGroup(Group group) {
        rwlock.writeLock().lock();
        try {
            var tuple = group.toTuple(true /* forUpdate */);
            var q = Query.updateTable(GROUP_TABLE_NAME)
                    .set(tuple)
                    .where(GROUP_CNAME_ID, group.getId());
            ydb.execute(q.toStatement());
        } catch (StreamSqlException | ParseException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void deleteGroup(Group group) {
        rwlock.writeLock().lock();
        try {
            var q = Query.deleteFromTable(GROUP_TABLE_NAME)
                    .where(GROUP_CNAME_ID, group.getId());
            ydb.execute(q.toStatement());
        } catch (StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.writeLock().unlock();
        }
    }
}
