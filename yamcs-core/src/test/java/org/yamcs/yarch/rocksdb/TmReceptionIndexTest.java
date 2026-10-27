package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.yamcs.archive.TmReceptionTimeIndex;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndex.State;

public class TmReceptionIndexTest extends YarchTestCase {
    private TableDefinition definition;
    private Tablespace tablespace;
    private long base = TimeEncoding.parse("2024-01-01T00:00:00Z");

    private void create(boolean index, boolean time) throws Exception {
        execute("create table tm(gentime timestamp, seqNum int, rectime timestamp, pname enum, packet binary, link enum,"
                + " primary key(gentime, seqNum)" + (index ? ", index(rectime,pname)" : "") + ") "
                + (time ? "partition by time_and_value(gentime('YYYY/MM'),pname)" : "partition by value(pname)")
                + " table_format=compressed,column_family:rt_data");
        definition = ydb.getTable("tm");
        tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
    }

    private void put(long genOffset, int seq, Long reception, String name, InsertMode mode) {
        var writer = tablespace.newTableWriter(ydb, definition, mode);
        try {
            writer.onTuple(null, new Tuple(definition.getTupleDefinition(),
                    new Object[] { base + genOffset, seq, reception, name, new byte[] { (byte) seq }, "link" }));
        } finally {
            writer.close();
        }
    }

    private List<Tuple> scan(boolean ascending, byte[] cursor, Long start, Long stop, Set<Object> names) throws Exception {
        var walker = new SecondaryIndexTableWalker(tablespace, tablespace.getTable(definition), ascending, false);
        DbRange range = new DbRange();
        range.rangeStart = start == null ? null : SecondaryIndexKey.prefix(definition, start);
        range.rangeEnd = stop == null ? null : SecondaryIndexKey.prefix(definition, stop);
        walker.setSecondaryIndexRange(range);
        walker.setStopBefore(range.rangeEnd);
        walker.setResumeAfter(cursor);
        walker.setPartitionFilter(null, names);
        List<Tuple> result = new ArrayList<>();
        walker.walk((key, value) -> {
            result.add(definition.deserialize(key, value));
            return TableVisitor.ACTION_CONTINUE;
        });
        return result;
    }

    @Test
    public void orderingBoundsCollisionsAndCursor() throws Exception {
        create(true, true);
        put(0, 1, base + 30, "a", InsertMode.INSERT);
        put(0, 1, base + 30, "b", InsertMode.INSERT);
        put(40L * 86400000, 2, base + 10, "a", InsertMode.INSERT);
        put(1, 3, base + 20, "a", InsertMode.INSERT);
        var rows = scan(true, null, null, null, null);
        assertEquals(List.of(base + 10, base + 20, base + 30, base + 30),
                rows.stream().map(t -> t.getColumn("rectime")).toList());
        assertEquals(List.of(base + 30, base + 30, base + 20, base + 10),
                scan(false, null, null, null, null).stream().map(t -> t.getColumn("rectime")).toList());
        assertEquals(1, scan(true, null, base + 20, base + 30, null).size());
        assertEquals(1, scan(false, null, base + 20, base + 30, null).size());
        for (boolean ascending : List.of(true, false)) {
            var ordered = scan(ascending, null, null, null, null);
            for (int i = 0; i < ordered.size(); i++) {
                assertEquals(ordered.size() - i - 1,
                        scan(ascending, SecondaryIndexKey.suffix(definition, ordered.get(i)), null, null, null).size());
            }
        }
        assertEquals(1, scan(true, null, null, null, Set.of(definition.getEnumValues("pname").get("b"))).size());
        var walker = new SecondaryIndexTableWalker(tablespace, tablespace.getTable(definition), true, false);
        walker.setResumeAfter(SecondaryIndexKey.suffix(definition, rows.get(2)));
        walker.walk((key, value) -> TableVisitor.ACTION_STOP);
        assertEquals(1, walker.getNumRecordsRead());
        assertEquals(1, walker.getNumPayloadReads());
    }

    @Test
    public void backfillAndMissingReception() throws Exception {
        create(false, true);
        put(0, 1, base + 20, "a", InsertMode.INSERT);
        put(40L * 86400000, 2, base + 10, "a", InsertMode.INSERT);
        put(1, 3, null, "b", InsertMode.INSERT);
        TmReceptionTimeIndex.rebuild(ydb).get(30, TimeUnit.SECONDS);
        assertEquals(2, scan(true, null, null, null, null).size());
        assertEquals(3, TmReceptionTimeIndex.get(ydb).getInfo().getProcessedRows());
        assertEquals(1, TmReceptionTimeIndex.get(ydb).getInfo().getMissingRows());
        put(2, 4, base + 30, "a", InsertMode.INSERT);
        TmReceptionTimeIndex.start(ydb, false);
        assertEquals(3, scan(true, null, null, null, null).size());
        reloadDb();
        definition = ydb.getTable("tm");
        tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
        assertEquals(State.READY, TmReceptionTimeIndex.get(ydb).getInfo().getState());
        assertEquals(3, scan(true, null, null, null, null).size());
    }

    @Test
    public void dedupReplacementLoadAndDeletion() throws Exception {
        create(true, true);
        put(0, 1, base + 10, "a", InsertMode.INSERT);
        put(0, 1, base + 99, "a", InsertMode.INSERT);
        assertEquals(base + 10, (long) scan(true, null, null, null, null).get(0).getColumn("rectime"));
        put(0, 1, base + 20, "a", InsertMode.UPSERT);
        assertEquals(0, scan(true, null, base, base + 20, null).size());
        assertEquals(1, scan(true, null, null, null, null).size());
        put(0, 1, base + 30, "a", InsertMode.LOAD);
        assertEquals(base + 30, (long) scan(true, null, null, null, null).get(0).getColumn("rectime"));
        execute("delete from tm where gentime = ?", base);
        assertEquals(0, scan(true, null, null, null, null).size());
    }

    @Test
    public void journalSurvivesBothCrashWindows() throws Exception {
        create(true, true);
        for (boolean afterIndex : List.of(false, true)) {
            var index = tablespace.getTable(definition).indexWriter;
            Runnable crash = () -> { throw new IllegalStateException("injected crash"); };
            if (afterIndex) {
                index.afterIndexCommit = crash;
            } else {
                index.afterSourceCommit = crash;
            }
            assertThrows(IllegalStateException.class,
                    () -> put(afterIndex ? 1 : 0, afterIndex ? 2 : 1, base + 10, "a", InsertMode.INSERT));
            reloadDb();
            definition = ydb.getTable("tm");
            tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
            TmReceptionTimeIndex.rebuild(ydb).get(30, TimeUnit.SECONDS);
            assertEquals(afterIndex ? 2 : 1, scan(true, null, null, null, null).size());
        }
    }

    @Test
    public void nullableIndexAndPrimaryRange() throws Exception {
        create(true, false);
        put(0, 1, null, "a", InsertMode.INSERT);
        put(1, 2, base + 10, "a", InsertMode.INSERT);
        assertEquals(2, scan(true, null, null, null, null).size());
        var walker = new SecondaryIndexTableWalker(tablespace, tablespace.getTable(definition), true, false);
        DbRange primary = new DbRange();
        primary.rangeStart = definition.getColumnSerializer("gentime").toByteArray(base + 1);
        walker.setPrimaryIndexRange(primary);
        List<Tuple> result = new ArrayList<>();
        walker.walk((key, value) -> {
            result.add(definition.deserialize(key, value));
            return TableVisitor.ACTION_CONTINUE;
        });
        assertEquals(1, result.size());
    }

    @Test
    public void checkpointResumeAndConcurrentIngestion() throws Exception {
        create(false, true);
        for (int i = 0; i < 1200; i++) {
            put(i, i, base + i, "a", InsertMode.INSERT);
        }
        var index = tablespace.ensureSecondaryIndex(ydb, definition, TmReceptionTimeIndex.COLUMNS);
        var visits = new java.util.concurrent.atomic.AtomicInteger();
        index.beforeBackfillRow = () -> {
            if (visits.incrementAndGet() == 1101) {
                throw new IllegalStateException("interrupt after checkpoint");
            }
            if (visits.get() == 2) {
                assertThrows(org.yamcs.yarch.YarchException.class,
                        () -> scan(true, null, null, null, null));
                put(40L * 86400000, 5000, base + 5000, "b", InsertMode.INSERT);
            }
        };
        assertThrows(java.util.concurrent.ExecutionException.class, () -> index.rebuild(false).get(30, TimeUnit.SECONDS));
        assertEquals(State.FAILED, index.getInfo().getState());
        assertEquals(1000, index.getInfo().getProcessedRows());
        reloadDb();
        definition = ydb.getTable("tm");
        tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
        TmReceptionTimeIndex.start(ydb, false);
        TmReceptionTimeIndex.get(ydb).rebuild(false).get(30, TimeUnit.SECONDS);
        assertEquals(1201, scan(true, null, null, null, null).size());
        assertEquals(1201, TmReceptionTimeIndex.get(ydb).getInfo().getProcessedRows());
    }

    @Test
    public void optInAndConflictingIndex() throws Exception {
        create(false, false);
        TmReceptionTimeIndex.start(ydb, false);
        assertNull(TmReceptionTimeIndex.get(ydb));
        TmReceptionTimeIndex.start(ydb, true);
        TmReceptionTimeIndex.get(ydb).rebuild(false).get(30, TimeUnit.SECONDS);
        assertEquals(State.READY, TmReceptionTimeIndex.get(ydb).getInfo().getState());
        execute("drop table tm");
        execute("create table tm(gentime timestamp,seqNum int,rectime timestamp,pname enum,"
                + "primary key(gentime,seqNum),index(pname)) partition by value(pname)");
        assertThrows(IllegalArgumentException.class, () -> TmReceptionTimeIndex.start(ydb, true));
    }

    @Test
    public void rebuildReturnsBeforeExistingReadersDrain() throws Exception {
        create(false, true);
        put(0, 1, base + 10, "a", InsertMode.INSERT);
        TmReceptionTimeIndex.rebuild(ydb).get(30, TimeUnit.SECONDS);
        var table = tablespace.getTable(definition);
        table.indexLock.readLock().lock();
        java.util.concurrent.CompletableFuture<Void> rebuild;
        try {
            rebuild = table.indexWriter.rebuild(true);
            assertFalse(rebuild.isDone());
            assertSame(rebuild, table.indexWriter.rebuild(true));
            assertEquals(State.BUILDING, table.indexWriter.getInfo().getState());
        } finally {
            table.indexLock.readLock().unlock();
        }
        rebuild.get(30, TimeUnit.SECONDS);
        assertEquals(State.READY, table.indexWriter.getInfo().getState());
        assertEquals(1, scan(true, null, null, null, null).size());
    }
}
