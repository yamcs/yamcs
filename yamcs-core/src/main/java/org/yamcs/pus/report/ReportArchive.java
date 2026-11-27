package org.yamcs.pus.report;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.satellite.ReportSnapshot;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.SecondaryIndexKey;
import org.yamcs.yarch.streamsql.StreamSqlException;

/** Per-instance storage for open checkpoints and immutable finalized attempts. */
public final class ReportArchive {

    static final String TABLE = "satellite_reports";
    private static final long OPEN = TimeEncoding.INVALID_INSTANT;

    private final YarchDatabaseInstance db;
    private final TableDefinition table;
    private final boolean legacyLayout;

    public ReportArchive(YarchDatabaseInstance db) {
        this.db = db;
        db.executeUnchecked("create table if not exists " + TABLE
                + " (id string, reportType string, sortTime timestamp, observed boolean,"
                + " snapshot protobuf('" + ReportSnapshot.class.getName() + "'), primary key(id), index(sortTime))"
                + " partition by value(reportType) table_format=compressed").close();
        table = db.getTable(TABLE);
        // Early builds partitioned by a combination of report type and a configurable label.
        legacyLayout = table.getColumnDefinition("scope") != null;
    }

    /** Replaces the current open checkpoint, or finalizes the attempt exactly once. */
    public synchronized void save(ReportSnapshot snapshot, long sortTime, boolean finalized) {
        if (snapshot.getId().isEmpty() || snapshot.getType().isEmpty() || sortTime == OPEN) {
            throw new IllegalArgumentException("Report checkpoint requires an id, report type and sort time");
        }
        var existing = find(snapshot.getId());
        if (existing != null) {
            if (existing.getTimestampColumn("sortTime") != OPEN) {
                return;
            }
            if (!existing.getColumn("reportType").equals(snapshot.getType())) {
                throw new IllegalArgumentException("Report checkpoint cannot change type for " + snapshot.getId());
            }
        }
        var stored = snapshot.toBuilder().setSortTime(TimeEncoding.toProtobufTimestamp(sortTime)).build();
        long indexTime = finalized ? sortTime : OPEN;
        var row = new Tuple(table.getTupleDefinition(), storageColumns(stored, existing, indexTime));
        var writer = db.getStorageEngine(table).newTableWriter(db, table, InsertMode.UPSERT);
        try {
            writer.onTuple(null, row);
        } finally {
            writer.close();
        }
        // Yarch's stream writer reports failures through the crash handler. Ensure this worker sees a failed write.
        var committed = find(stored.getId());
        if (committed == null || committed.getTimestampColumn("sortTime") != indexTime
                || !committed.getColumn("snapshot").equals(stored)) {
            throw new YarchException("Could not store satellite report " + stored.getId());
        }
    }

    private Object[] storageColumns(ReportSnapshot snapshot, Tuple existing, long indexTime) {
        if (legacyLayout) {
            // Keep the old partition for checkpoints written before instance-only scoping.
            String instanceLabel = existing == null ? db.getYamcsInstance() : existing.getColumn("target");
            String partition = existing == null ? snapshot.getType() + ':' + instanceLabel : existing.getColumn("scope");
            return new Object[] { snapshot.getId(), snapshot.getType(), instanceLabel, partition,
                    indexTime, snapshot.hasLastReceived(), snapshot };
        }
        return new Object[] { snapshot.getId(), snapshot.getType(), indexTime, snapshot.hasLastReceived(), snapshot };
    }

    /** Returns the specified finalized attempt, or null when it is unavailable. */
    public ReportSnapshot get(String id) {
        var tuple = find(id);
        return tuple == null || tuple.getTimestampColumn("sortTime") == OPEN ? null : tuple.getColumn("snapshot");
    }

    /** Lists finalized attempts in descending (sortTime, id) order. Start is inclusive and stop is exclusive. */
    public List<ReportSnapshot> list(String type, Long start, Long stop,
            Long beforeTime, String beforeId, int limit, boolean observedOnly) {
        if (limit <= 0) {
            return List.of();
        }
        var query = new SqlBuilder(TABLE).select("snapshot").descend(true).limit(limit);
        // The time index also orders ties by primary key. The earliest index value is reserved for checkpoints.
        query.where("sortTime > ?", OPEN);
        if (type != null) {
            query.where("reportType = ?", type);
        }
        if (start != null) {
            query.where("sortTime >= ?", start);
        }
        if (stop != null) {
            query.where("sortTime < ?", stop);
        }
        if (beforeTime != null && (stop == null || beforeTime < stop)) {
            query.where("sortTime <= ?", beforeTime)
                    .where("(sortTime < ? or id < ?)", beforeTime, beforeId);
        }
        if (observedOnly) {
            query.where("observed = ?", true);
        }
        var result = db.executeUnchecked(query.toString(), query.getQueryArgumentsArray());
        List<ReportSnapshot> snapshots = new ArrayList<>();
        try {
            result.forEachRemaining(tuple -> snapshots.add(tuple.getColumn("snapshot")));
        } finally {
            result.close();
        }
        return snapshots;
    }

    ReportSnapshot findLegacyAttempt(ReportSnapshot attempt, long cutoff) {
        if (!legacyLayout || !attempt.hasCommand()) {
            return null;
        }

        Long beforeTime = null;
        String beforeId = null;
        int batchSize = 100;
        for (;;) {
            var snapshots = list(attempt.getType(), cutoff, null, beforeTime, beforeId, batchSize, false);
            for (var snapshot : snapshots) {
                if (snapshot.hasCommand() && snapshot.getCommand().getId().equals(attempt.getCommand().getId())) {
                    return snapshot;
                }
            }
            if (snapshots.size() < batchSize) {
                return null;
            }
            var last = snapshots.getLast();
            beforeTime = TimeEncoding.fromProtobufTimestamp(last.getSortTime());
            beforeId = last.getId();
        }
    }

    /** Returns durable checkpoints that need to be finalized on service restart. */
    public List<ReportSnapshot> open() {
        var walker = db.getStorageEngine(table).newSecondaryIndexTableWalker(db, table, true, false);
        var range = new DbRange();
        range.rangeStart = SecondaryIndexKey.prefix(table, OPEN);
        range.rangeEnd = range.rangeStart;
        walker.setSecondaryIndexRange(range);
        List<ReportSnapshot> snapshots = new ArrayList<>();
        try {
            walker.walk((key, value) -> {
                snapshots.add(table.deserialize(key, value).getColumn("snapshot"));
                return TableVisitor.ACTION_CONTINUE;
            });
        } catch (StreamSqlException e) {
            throw new YarchException(e);
        } finally {
            walker.close();
        }
        return snapshots;
    }

    private Tuple find(String id) {
        var result = db.executeUnchecked("select * from " + TABLE + " where id = ? limit 1", id);
        try {
            return result.hasNext() ? result.next() : null;
        } finally {
            result.close();
        }
    }
}
