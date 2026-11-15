package org.yamcs.activities.triggers;

import static org.yamcs.activities.triggers.TriggerFiring.CNAME_ACTIVITY_ID;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_CONTEXT;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_DECIDED_BY;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_ID;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_MESSAGE;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_OUTCOME;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_SEQ;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_TIME;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_TRIGGER_ID;
import static org.yamcs.activities.triggers.TriggerFiring.CNAME_TRIGGER_NAME;
import static org.yamcs.yarch.query.Query.createStream;
import static org.yamcs.yarch.query.Query.createTable;
import static org.yamcs.yarch.query.Query.deleteFromTable;
import static org.yamcs.yarch.query.Query.selectStream;
import static org.yamcs.yarch.query.Query.selectTable;
import static org.yamcs.yarch.query.Query.upsertIntoTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.yamcs.InitException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.activities.TriggerFiringOutcome;
import org.yamcs.protobuf.activities.TriggerInfo;
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

/**
 * Persistence of trigger definitions and trigger firings.
 */
public class TriggerDb {

    public static final String TRIGGER_TABLE_NAME = "activity_trigger";
    public static final String FIRING_TABLE_NAME = "activity_trigger_firing";

    private static final String CNAME_DEFINITION = "definition";

    private static final TupleDefinition TRIGGER_TDEF = new TupleDefinition();
    private static final TupleDefinition FIRING_TDEF = new TupleDefinition();
    static {
        TRIGGER_TDEF.addColumn(CNAME_ID, DataType.UUID);
        TRIGGER_TDEF.addColumn(CNAME_DEFINITION, DataType.protobuf(TriggerInfo.class));

        FIRING_TDEF.addColumn(CNAME_TIME, DataType.TIMESTAMP);
        FIRING_TDEF.addColumn(CNAME_SEQ, DataType.INT);
        FIRING_TDEF.addColumn(CNAME_ID, DataType.UUID);
        FIRING_TDEF.addColumn(CNAME_TRIGGER_ID, DataType.UUID);
        FIRING_TDEF.addColumn(CNAME_TRIGGER_NAME, DataType.STRING);
        FIRING_TDEF.addColumn(CNAME_OUTCOME, DataType.STRING);
        FIRING_TDEF.addColumn(CNAME_CONTEXT, DataType.protobuf(Struct.class));
        FIRING_TDEF.addColumn(CNAME_ACTIVITY_ID, DataType.UUID);
        FIRING_TDEF.addColumn(CNAME_DECIDED_BY, DataType.STRING);
        FIRING_TDEF.addColumn(CNAME_MESSAGE, DataType.STRING);
    }

    private final Log log;
    private final YarchDatabaseInstance ydb;
    private final Stream triggerStream;
    private final Stream firingStream;

    public TriggerDb(String yamcsInstance) throws InitException {
        log = new Log(TriggerDb.class, yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            triggerStream = setupTable(TRIGGER_TABLE_NAME, TRIGGER_TDEF, new String[] { CNAME_ID }, null);
            firingStream = setupTable(FIRING_TABLE_NAME, FIRING_TDEF, new String[] { CNAME_TIME, CNAME_SEQ },
                    CNAME_ID);
        } catch (StreamSqlException | ParseException e) {
            throw new InitException(e);
        }
    }

    private Stream setupTable(String tableName, TupleDefinition tdef, String[] primaryKey, String index)
            throws StreamSqlException, ParseException {
        var streamName = tableName + "_in";
        var table = ydb.getTable(tableName);
        if (table == null) {
            var q = createTable(tableName, tdef).primaryKey(primaryKey);
            if (index != null) {
                q.index(index);
            }
            ydb.execute(q.toStatement());
        } else {
            table.addMissingValueColumns(tdef);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute(createStream(streamName, tdef).toStatement());
        }
        ydb.execute(upsertIntoTable(tableName).query(selectStream(streamName).toSQL()).toStatement());
        return ydb.getStream(streamName);
    }

    public synchronized List<TriggerInfo> listTriggers() {
        var result = new ArrayList<TriggerInfo>();
        var r = ydb.executeUnchecked(selectTable(TRIGGER_TABLE_NAME).toStatement());
        try {
            while (r.hasNext()) {
                var tuple = r.next();
                result.add(tuple.getColumn(CNAME_DEFINITION));
            }
        } finally {
            r.close();
        }
        return result;
    }

    public synchronized void saveTrigger(TriggerInfo trigger) {
        var tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, UUID.fromString(trigger.getId()));
        tuple.addColumn(CNAME_DEFINITION, DataType.protobuf(TriggerInfo.class), trigger);
        triggerStream.emitTuple(tuple);
    }

    public synchronized void deleteTrigger(UUID id) {
        var r = ydb.executeUnchecked(deleteFromTable(TRIGGER_TABLE_NAME).where(CNAME_ID, id).toStatement());
        r.close();
    }

    public synchronized void saveFiring(TriggerFiring firing) {
        log.trace("Saving firing {}", firing);
        firingStream.emitTuple(firing.toTuple());
    }

    public synchronized TriggerFiring getFiring(UUID id) {
        var r = ydb.executeUnchecked(selectTable(FIRING_TABLE_NAME).where(CNAME_ID, id).toStatement());
        try {
            if (r.hasNext()) {
                return new TriggerFiring(r.next());
            }
        } finally {
            r.close();
        }
        return null;
    }

    /**
     * List firings, most recent first.
     */
    public synchronized List<TriggerFiring> listFirings(UUID triggerId, Collection<TriggerFiringOutcome> outcomes,
            Long start, Long stop, int limit) {
        var sqlb = new SqlBuilder(FIRING_TABLE_NAME);
        if (triggerId != null) {
            sqlb.where(CNAME_TRIGGER_ID + " = ?", triggerId);
        }
        if (outcomes != null && !outcomes.isEmpty()) {
            sqlb.whereColIn(CNAME_OUTCOME, outcomes.stream().map(Enum::name).toList());
        }
        if (start != null) {
            sqlb.whereColAfterOrEqual(CNAME_TIME, start);
        }
        if (stop != null) {
            sqlb.whereColBefore(CNAME_TIME, stop);
        }
        sqlb.descend(true);
        sqlb.limit(limit);

        var result = new ArrayList<TriggerFiring>();
        try {
            var r = ydb.execute(sqlb.toString(), sqlb.getQueryArguments().toArray());
            try {
                while (r.hasNext()) {
                    result.add(new TriggerFiring(r.next()));
                }
            } finally {
                r.close();
            }
        } catch (StreamSqlException | ParseException e) {
            log.error("Failed to list trigger firings", e);
        }
        return result;
    }

    /**
     * Deletes firings older than the specified time
     */
    public synchronized void deleteFiringsBefore(long time) {
        try {
            var r = ydb.execute("delete from " + FIRING_TABLE_NAME + " where " + CNAME_TIME + " < ?", time);
            r.close();
        } catch (StreamSqlException | ParseException e) {
            log.error("Failed to delete old trigger firings", e);
        }
    }
}
