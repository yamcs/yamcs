package org.yamcs.activities;

import static org.yamcs.yarch.query.Query.createStream;
import static org.yamcs.yarch.query.Query.createTable;
import static org.yamcs.yarch.query.Query.selectStream;
import static org.yamcs.yarch.query.Query.upsertIntoTable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.logging.Log;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class ActivityLogDb {

    public static final String TABLE_NAME = "activity_log";
    private static final TupleDefinition TDEF = new TupleDefinition();

    public static final String CNAME_TIME = "time";
    public static final String CNAME_ACTIVITY_ID = "activity_id";
    public static final String CNAME_SEQ = "seq";
    public static final String CNAME_SOURCE = "source";
    public static final String CNAME_LEVEL = "level";
    public static final String CNAME_MESSAGE = "message";
    static {
        TDEF.addColumn(CNAME_TIME, DataType.TIMESTAMP);
        TDEF.addColumn(CNAME_ACTIVITY_ID, DataType.UUID);
        TDEF.addColumn(CNAME_SEQ, DataType.LONG);
        TDEF.addColumn(CNAME_SOURCE, DataType.ENUM);
        TDEF.addColumn(CNAME_LEVEL, DataType.STRING);
        TDEF.addColumn(CNAME_MESSAGE, DataType.STRING);
    }

    private Log log;
    private YarchDatabaseInstance ydb;
    private Stream tableStream;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    public ActivityLogDb(String yamcsInstance) throws InitException {
        log = new Log(ActivityDb.class, yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);

        try {
            var streamName = TABLE_NAME + "_in";
            if (ydb.getTable(TABLE_NAME) == null) {
                var q = createTable(TABLE_NAME, TDEF)
                        .autoIncrement(CNAME_SEQ)
                        .primaryKey(CNAME_TIME, CNAME_ACTIVITY_ID, CNAME_SEQ)
                        .index(CNAME_ACTIVITY_ID);
                ydb.execute(q.toStatement());
            }
            if (ydb.getStream(streamName) == null) {
                var q = createStream(streamName, TDEF);
                ydb.execute(q.toStatement());
            }

            var q = upsertIntoTable(TABLE_NAME)
                    .query(selectStream(streamName).toSQL());
            ydb.execute(q.toStatement());

            tableStream = ydb.getStream(streamName);
        } catch (StreamSqlException | ParseException e) {
            throw new InitException(e);
        }
    }

    public void addLogEntry(ActivityLog logEntry) {
        rwlock.writeLock().lock();
        try {
            var tuple = logEntry.toTuple();
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public List<ActivityLog> getLogEntries(UUID activityId) {
        var logEntries = new ArrayList<ActivityLog>();
        rwlock.readLock().lock();
        try {
            var sqlBuilder = new SqlBuilder(TABLE_NAME);
            sqlBuilder.where("activity_id = ?", activityId);

            var stmt = ydb.createStatement(sqlBuilder.toString(),
                    sqlBuilder.getQueryArguments().toArray());
            var result = ydb.execute(stmt);
            result.forEachRemaining(tuple -> {
                logEntries.add(new ActivityLog(tuple));
            });
            result.close();
        } catch (StreamSqlException | ParseException e) {
            log.error("Exception when executing query", e);
        } finally {
            rwlock.readLock().unlock();
        }
        return logEntries;
    }
}
