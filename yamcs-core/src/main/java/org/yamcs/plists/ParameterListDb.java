package org.yamcs.plists;

import static org.yamcs.yarch.query.Query.createStream;
import static org.yamcs.yarch.query.Query.createTable;
import static org.yamcs.yarch.query.Query.deleteFromTable;
import static org.yamcs.yarch.query.Query.selectStream;
import static org.yamcs.yarch.query.Query.selectTable;

import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.logging.Log;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.query.Query;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class ParameterListDb {

    public static final String TABLE_NAME = "parameter_list";
    private static final TupleDefinition TDEF = new TupleDefinition();
    public static final String CNAME_ID = "id";
    public static final String CNAME_NAME = "name";
    public static final String CNAME_DESCRIPTION = "description";
    public static final String CNAME_PATTERNS = "patterns";
    static {
        TDEF.addColumn(CNAME_ID, DataType.UUID);
        TDEF.addColumn(CNAME_NAME, DataType.STRING);
        TDEF.addColumn(CNAME_DESCRIPTION, DataType.STRING);
        TDEF.addColumn(CNAME_PATTERNS, DataType.array(DataType.STRING));
    }

    private Log log;
    private YarchDatabaseInstance ydb;
    private Stream tableStream;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    public ParameterListDb(String yamcsInstance) throws InitException {
        log = new Log(AuditLog.class, yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            String streamName = TABLE_NAME + "_in";
            if (ydb.getTable(TABLE_NAME) == null) {
                var q = createTable(TABLE_NAME, TDEF)
                        .primaryKey(CNAME_ID);
                ydb.execute(q.toStatement());
            }
            if (ydb.getStream(streamName) == null) {
                var q = createStream(streamName, TDEF);
                ydb.execute(q.toStatement());
            }

            var q = Query.upsertIntoTable(TABLE_NAME)
                    .query(selectStream(streamName).toSQL());
            ydb.execute(q.toStatement());

            tableStream = ydb.getStream(streamName);
        } catch (StreamSqlException | ParseException e) {
            throw new InitException(e);
        }
    }

    public ParameterList getById(UUID id) {
        rwlock.readLock().lock();
        try {
            var query = selectTable(TABLE_NAME).where(CNAME_ID, id);
            var r = ydb.executeUnchecked(query.toStatement());
            try {
                if (r.hasNext()) {
                    Tuple tuple = r.next();
                    try {
                        var plist = new ParameterList(tuple);
                        log.trace("Read parameter list from db {}", plist);
                        return plist;
                    } catch (Exception e) {
                        log.error("Cannot decode tuple {} into parameter list", tuple);
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

    public void insert(ParameterList parameterList) {
        rwlock.writeLock().lock();
        try {
            var tuple = parameterList.toTuple();
            log.trace("Adding parameter list: {}", tuple);
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void update(ParameterList parameterList) {
        rwlock.writeLock().lock();
        try {
            var tuple = parameterList.toTuple();
            log.trace("Updating parameter list: {}", tuple);
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void delete(UUID parameterListId) {
        rwlock.writeLock().lock();
        try {
            var query = deleteFromTable(TABLE_NAME).where(CNAME_ID, parameterListId);
            var result = ydb.executeUnchecked(query.toStatement());
            result.close();
        } finally {
            rwlock.writeLock().unlock();
        }
    }
}
