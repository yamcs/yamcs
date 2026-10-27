package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.yamcs.archive.TmReceptionFixture;
import org.yamcs.archive.TmReceptionTimeIndex;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

/** Small reproducible benchmark; run explicitly, never use wall-clock thresholds as assertions. */
public class TmReceptionPerformanceTest extends YarchTestCase {
    private static final int COUNT = 3000;
    private static final int PAGE = 100;

    private record Sample(double millis, long entries, long reads, byte[] cursor) { }

    @Test
    public void indexedPagesSeekAndReportCosts() throws Exception {
        var definition = TmReceptionFixture.create(ydb);
        var tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
        long started = System.nanoTime();
        populate(0, COUNT);
        double baselineWrites = COUNT * 1e9 / (System.nanoTime() - started);
        started = System.nanoTime();
        TmReceptionTimeIndex.rebuild(ydb).get(30, TimeUnit.SECONDS);
        double backfillRate = COUNT * 1e9 / (System.nanoTime() - started);
        started = System.nanoTime();
        populate(COUNT, 1000);
        double indexedWrites = 1000 * 1e9 / (System.nanoTime() - started);

        // Warm both readers and alternate measurements to reduce ordering effects.
        primary(null);
        secondary(null, false);
        List<Double> generationTimes = new ArrayList<>();
        List<Double> receptionTimes = new ArrayList<>();
        Sample firstReception = null;
        for (int i = 0; i < 20; i++) {
            generationTimes.add(primary(null).millis());
            firstReception = secondary(null, false);
            receptionTimes.add(firstReception.millis());
        }
        Sample laterGeneration = primary(primary(null).cursor());
        Sample laterReception = secondary(firstReception.cursor(), false);
        Sample filtered = secondary(null, true);
        assertEquals(PAGE + 1, firstReception.entries());
        assertEquals(PAGE + 1, laterReception.entries());
        assertEquals(PAGE + 1, laterReception.reads());

        long indexBytes = 0;
        long indexEntries = 0;
        byte[] prefix = RdbStorageEngine.dbKey(TmReceptionTimeIndex.get(ydb).getTbsIndex());
        try (var iterator = new AscendingRangeIterator(tablespace.getRdb().newIterator(), prefix, prefix)) {
            while (iterator.isValid()) {
                indexEntries++;
                indexBytes += iterator.key().length + iterator.value().length;
                iterator.next();
            }
        }
        assertEquals(COUNT + 1000, indexEntries);

        // Leave source commits unapplied to exercise real restart recovery, separately from backfill.
        var index = tablespace.getTable(definition).indexWriter;
        index.afterSourceCommit = () -> { throw new IllegalStateException("benchmark crash window"); };
        final int recoveryRows = 100;
        for (int i = COUNT + 1000; i < COUNT + 1000 + recoveryRows; i++) {
            final int row = i;
            assertThrows(IllegalStateException.class, () -> populate(row, 1));
        }
        started = System.nanoTime();
        reloadDb();
        TmReceptionTimeIndex.get(ydb).rebuild(false).get(30, TimeUnit.SECONDS);
        double recoveryRate = recoveryRows * 1e9 / (System.nanoTime() - started);
        var recoveredTable = RdbStorageEngine.getInstance().getTablespace(ydb).getTable(ydb.getTable("tm"));
        var recoveredWalker = new SecondaryIndexTableWalker(recoveredTable.tablespace, recoveredTable, true, false);
        int[] recoveredCount = { 0 };
        recoveredWalker.walk((key, value) -> { recoveredCount[0]++; return TableVisitor.ACTION_CONTINUE; });
        assertEquals(COUNT + 1000 + recoveryRows, recoveredCount[0]);

        System.out.printf("TM_RECEPTION_BENCH rows=%d partitions=%d page=%d "
                + "generation_median_ms=%.3f reception_median_ms=%.3f generation_later_ms=%.3f reception_later_ms=%.3f "
                + "reception_entries=%d reception_reads=%d later_entries=%d later_reads=%d "
                + "filtered_entries=%d filtered_reads=%d ingest_without_index_rows_s=%.1f ingest_with_index_rows_s=%.1f "
                + "backfill_rows_s=%.1f logical_index_bytes=%d recovery_rows=%d recovery_rows_s=%.1f%n",
                indexEntries, recoveredTable.partitionManager.getPartitions().size(), PAGE,
                median(generationTimes), median(receptionTimes), laterGeneration.millis(), laterReception.millis(),
                firstReception.entries(), firstReception.reads(), laterReception.entries(), laterReception.reads(),
                filtered.entries(), filtered.reads(), baselineWrites, indexedWrites, backfillRate, indexBytes,
                recoveryRows, recoveryRate);
    }

    private void populate(int offset, int count) {
        for (int i = offset; i < offset + count; i++) {
            long generation = TmReceptionFixture.BASE + (i % 3) * 40L * 86400000 + i * 1000L;
            TmReceptionFixture.put(ydb, generation, i, TmReceptionFixture.BASE + i * 1000L,
                    "name" + i % 7, i % 4 == 0 ? "selected" : "other");
        }
    }

    private Sample primary(byte[] cursor) throws Exception {
        var definition = ydb.getTable("tm");
        long started = System.nanoTime();
        try (var context = new ExecutionContext(ydb)) {
            var walker = (RdbTableWalker) ydb.getStorageEngine(definition).newTableWalker(context, definition, false, false);
            if (cursor != null) {
                var range = new DbRange();
                range.rangeEnd = cursor;
                walker.setPrimaryIndexRange(range);
            }
            int[] count = { 0 };
            byte[][] last = { null };
            walker.walk((key, value) -> {
                if (cursor != null && Arrays.equals(key, cursor)) {
                    return TableVisitor.ACTION_CONTINUE;
                }
                if (++count[0] <= PAGE) { last[0] = key; }
                return count[0] == PAGE + 1 ? TableVisitor.ACTION_STOP : TableVisitor.ACTION_CONTINUE;
            });
            return new Sample((System.nanoTime() - started) / 1e6, walker.getNumRecordsRead(),
                    walker.getNumRecordsRead(), last[0]);
        }
    }

    private Sample secondary(byte[] cursor, boolean filter) throws Exception {
        var definition = ydb.getTable("tm");
        var tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
        var walker = new SecondaryIndexTableWalker(tablespace, tablespace.getTable(definition), false, false);
        walker.setResumeAfter(cursor);
        int[] count = { 0 };
        byte[][] last = { null };
        long started = System.nanoTime();
        walker.walk((key, value) -> {
            Tuple tuple = definition.deserialize(key, value);
            if (filter && (!"selected".equals(tuple.getColumn("link"))
                    || !"name0".equals(tuple.getColumn("pname")))) {
                return TableVisitor.ACTION_CONTINUE;
            }
            if (++count[0] <= PAGE) { last[0] = walker.getLastKey(); }
            return count[0] == PAGE + 1 ? TableVisitor.ACTION_STOP : TableVisitor.ACTION_CONTINUE;
        });
        return new Sample((System.nanoTime() - started) / 1e6, walker.getNumRecordsRead(), walker.getNumPayloadReads(), last[0]);
    }

    private double median(List<Double> values) {
        return values.stream().sorted().toList().get(values.size() / 2);
    }
}
