package org.yamcs.web.db;

import static org.yamcs.yarch.query.Query.createStream;
import static org.yamcs.yarch.query.Query.createTable;
import static org.yamcs.yarch.query.Query.deleteFromTable;
import static org.yamcs.yarch.query.Query.selectStream;
import static org.yamcs.yarch.query.Query.selectTable;
import static org.yamcs.yarch.query.Query.upsertIntoTable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.YamcsServerInstance;
import org.yamcs.logging.Log;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.protobuf.Struct;

public class QueryDb {

    public static final String TABLE_NAME = "web_query";
    private static final TupleDefinition TDEF = new TupleDefinition();
    public static final String CNAME_ID = "id";
    public static final String CNAME_RESOURCE = "resource";
    public static final String CNAME_NAME = "name";
    public static final String CNAME_USER_ID = "user_id";
    public static final String CNAME_QUERY = "query";
    public static final DataType STRUCT_TYPE = DataType.protobuf(Struct.class.getName());
    private static ConcurrentMap<String, QueryDb> dbs = new ConcurrentHashMap<>();
    static {
        TDEF.addColumn(CNAME_ID, DataType.UUID);
        TDEF.addColumn(CNAME_RESOURCE, DataType.STRING);
        TDEF.addColumn(CNAME_NAME, DataType.STRING);
        TDEF.addColumn(CNAME_USER_ID, DataType.LONG);
        TDEF.addColumn(CNAME_QUERY, STRUCT_TYPE);

        ManagementService.getInstance().addManagementListener(new ManagementListener() {
            @Override
            public void instanceStateChanged(YamcsServerInstance ysi) {
                switch (ysi.state()) {
                case OFFLINE:
                case FAILED:
                    dbs.remove(ysi.getName());
                    break;
                default:
                    // Ignore
                }
            }
        });
    }

    private Log log;
    private YarchDatabaseInstance ydb;
    private Stream tableStream;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private QueryDb(String yamcsInstance) throws StreamSqlException, ParseException {
        log = new Log(QueryDb.class, yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);

        var streamName = TABLE_NAME + "_in";
        if (ydb.getTable(TABLE_NAME) == null) {
            var q = createTable(TABLE_NAME, TDEF)
                    .primaryKey(CNAME_ID);
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
    }

    public Query getById(UUID id) {
        rwlock.readLock().lock();
        try {
            var q = selectTable(TABLE_NAME).where(CNAME_ID, id);
            var r = ydb.executeUnchecked(q.toStatement());
            try {
                if (r.hasNext()) {
                    Tuple tuple = r.next();
                    try {
                        var query = new Query(tuple);
                        log.trace("Read query from db {}", query);
                        return query;
                    } catch (Exception e) {
                        log.error("Cannot decode tuple {} into query", tuple);
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

    public void insert(Query query) {
        rwlock.writeLock().lock();
        try {
            var tuple = query.toTuple();
            log.trace("Adding query: {}", tuple);
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void update(Query query) {
        rwlock.writeLock().lock();
        try {
            var tuple = query.toTuple();
            log.trace("Updating query: {}", tuple);
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void delete(UUID queryId) {
        rwlock.writeLock().lock();
        try {
            var query = deleteFromTable(TABLE_NAME).where(CNAME_ID, queryId);
            var result = ydb.executeUnchecked(query.toStatement());
            result.close();
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * Retrieve a {@link QueryDb} for the given Yamcs instance.
     */
    public static QueryDb getInstance(String yamcsInstance) {
        return dbs.computeIfAbsent(yamcsInstance, x -> {
            try {
                return new QueryDb(yamcsInstance);
            } catch (StreamSqlException | ParseException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
