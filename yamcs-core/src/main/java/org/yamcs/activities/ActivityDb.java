package org.yamcs.activities;

import static org.yamcs.yarch.query.Query.createStream;
import static org.yamcs.yarch.query.Query.createTable;
import static org.yamcs.yarch.query.Query.deleteFromTable;
import static org.yamcs.yarch.query.Query.selectStream;
import static org.yamcs.yarch.query.Query.selectTable;
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
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.protobuf.Struct;

public class ActivityDb {

    public static final String TABLE_NAME = "activity";
    private static final TupleDefinition TDEF = new TupleDefinition();

    public static final String CNAME_START = "start";
    public static final String CNAME_SEQ = "seq";
    public static final String CNAME_ID = "id";
    public static final String CNAME_TYPE = "type";
    public static final String CNAME_ARGS = "args";
    public static final String CNAME_STATUS = "status";
    public static final String CNAME_DETAIL = "detail";
    public static final String CNAME_STOP = "stop";
    public static final String CNAME_STARTED_BY = "started_by";
    public static final String CNAME_STOPPED_BY = "stopped_by";
    public static final String CNAME_FAILURE_REASON = "failure_reason";
    public static final String CNAME_COMMENT = "comment";
    static {
        TDEF.addColumn(CNAME_START, DataType.TIMESTAMP);
        TDEF.addColumn(CNAME_SEQ, DataType.INT);
        TDEF.addColumn(CNAME_ID, DataType.UUID);
        TDEF.addColumn(CNAME_TYPE, DataType.STRING);
        TDEF.addColumn(CNAME_ARGS, DataType.protobuf(Struct.class));
        TDEF.addColumn(CNAME_STATUS, DataType.STRING);
        TDEF.addColumn(CNAME_DETAIL, DataType.STRING);
        TDEF.addColumn(CNAME_STARTED_BY, DataType.STRING);
        TDEF.addColumn(CNAME_STOP, DataType.TIMESTAMP);
        TDEF.addColumn(CNAME_FAILURE_REASON, DataType.STRING);
        TDEF.addColumn(CNAME_STOPPED_BY, DataType.STRING);
        TDEF.addColumn(CNAME_COMMENT, DataType.STRING);
    }

    private Log log;
    private YarchDatabaseInstance ydb;
    private Stream tableStream;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    public ActivityDb(String yamcsInstance) throws InitException {
        log = new Log(ActivityDb.class, yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);

        try {
            var streamName = TABLE_NAME + "_in";
            if (ydb.getTable(TABLE_NAME) == null) {
                var q = createTable(TABLE_NAME, TDEF)
                        .primaryKey(CNAME_START, CNAME_SEQ)
                        .index(CNAME_ID);
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

    public Activity getById(UUID id) {
        rwlock.readLock().lock();
        try {
            var query = selectTable(TABLE_NAME).where(CNAME_ID, id);
            var r = ydb.executeUnchecked(query.toStatement());
            try {
                if (r.hasNext()) {
                    Tuple tuple = r.next();
                    try {
                        var activity = new Activity(tuple);
                        log.trace("Read activity from db {}", activity);
                        return activity;
                    } catch (Exception e) {
                        log.error("Cannot decode tuple {} into activity", tuple);
                    }
                }
            } finally {
                r.close();

            }
            return null;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Returns all activities without a stop time
     */
    public List<Activity> getUnfinishedActivities() {
        var unstoppedActivities = new ArrayList<Activity>();
        rwlock.readLock().lock();
        try {
            var sqlBuilder = new SqlBuilder(TABLE_NAME);
            sqlBuilder.where("stop is null");

            var stmt = ydb.createStatement(sqlBuilder.toString(),
                    sqlBuilder.getQueryArguments().toArray());
            var result = ydb.execute(stmt);
            result.forEachRemaining(tuple -> {
                unstoppedActivities.add(new Activity(tuple));
            });
            result.close();
        } catch (StreamSqlException | ParseException e) {
            log.error("Exception when executing query", e);
        } finally {
            rwlock.readLock().unlock();
        }
        return unstoppedActivities;
    }

    public void insert(Activity activity) {
        rwlock.writeLock().lock();
        try {
            var tuple = activity.toTuple();
            log.trace("Adding activity: {}", tuple);
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void update(Activity activity) {
        rwlock.writeLock().lock();
        try {
            var tuple = activity.toTuple();
            log.trace("Updating activity: {}", tuple);
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void updateAll(List<Activity> activities) {
        rwlock.writeLock().lock();
        try {
            for (var activity : activities) {
                var tuple = activity.toTuple();
                log.trace("Updating activity: {}", tuple);
                tableStream.emitTuple(tuple);
            }
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void deleteActivity(UUID id) {
        rwlock.writeLock().lock();
        try {
            var query = deleteFromTable(TABLE_NAME).where(CNAME_ID, id);
            var result = ydb.executeUnchecked(query.toStatement());
            result.close();
        } finally {
            rwlock.writeLock().unlock();
        }
    }
}
