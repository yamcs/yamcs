package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.yamcs.parameterarchive.ParameterArchive.getIntervalStart;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.parameterarchive.ParameterArchiveStorage.ScanRequest;
import org.yamcs.parameterarchive.ParameterArchiveStorage.SegmentRecord;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.TimePartitionSchema;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

/**
 * Tests the storage independently of the segment encoding: the values are arbitrary bytes.
 */
public class RdbParameterArchiveStorageTest {
    static final int PG = 3;
    static final TimePartitionSchema MONTHLY = TimePartitionSchema.getInstance("YYYY/MM");

    Tablespace tablespace;
    RdbParameterArchiveStorage storage;
    int timePid, pidA, pidB;
    // two intervals in January, one in February (different partitions)
    long i1, i2, i3;

    @BeforeAll
    public static void beforeAll() {
        TimeEncoding.setUp();
    }

    @BeforeEach
    public void before() throws Exception {
        Path f = Path.of(System.getProperty("java.io.tmpdir"), "RdbParameterArchiveStorageTest");
        FileUtils.deleteRecursivelyIfExists(f);

        tablespace = new Tablespace("test1");
        tablespace.setCustomDataDir(f.toString());
        tablespace.loadDb(false);
        storage = RdbParameterArchiveStorage.open("test1", tablespace, MONTHLY);

        timePid = newPid("time");
        pidA = newPid("a");
        pidB = newPid("b");

        i1 = getIntervalStart(TimeEncoding.parse("2024-01-05T00:00:00"));
        i2 = getIntervalStart(TimeEncoding.parse("2024-01-20T00:00:00"));
        i3 = getIntervalStart(TimeEncoding.parse("2024-02-10T00:00:00"));
    }

    @AfterEach
    public void after() {
        tablespace.close();
    }

    int newPid(String name) {
        var trb = TablespaceRecord.newBuilder().setType(TablespaceRecord.Type.PARCHIVE_DATA).setParameterFqn(name);
        return storage.createRecord(trb).getTbsIndex();
    }

    static byte[] v(int pid, long interval, byte type) {
        return ("v" + pid + "-" + interval + "-" + type).getBytes();
    }

    void write(long interval, int... pids) {
        try (var wb = storage.newWriteBatch(interval)) {
            assertEquals(ParameterArchive.VERSION, wb.version());
            wb.put(SegmentKey.encode(timePid, PG, interval, SegmentKey.TYPE_ENG_VALUE),
                    v(timePid, interval, SegmentKey.TYPE_ENG_VALUE));
            for (int pid : pids) {
                for (byte type : new byte[] { SegmentKey.TYPE_ENG_VALUE, SegmentKey.TYPE_PARAMETER_STATUS,
                        SegmentKey.TYPE_GAPS }) {
                    wb.put(SegmentKey.encode(pid, PG, interval, type), v(pid, interval, type));
                }
            }
            wb.commit();
        }
    }

    void writeAll() {
        write(i1, pidA);
        write(i2, pidB);
        write(i3, pidA, pidB);
    }

    List<SegmentRecord> scan(long start, long stop, boolean ascending, boolean status, int... pids) {
        int[] pgids = new int[pids.length];
        java.util.Arrays.fill(pgids, PG);
        List<SegmentRecord> r = new ArrayList<>();
        try (var scanner = storage.scan(new ScanRequest(timePid, pids, pgids, start, stop, ascending, true, true,
                status))) {
            for (; scanner.isValid(); scanner.next()) {
                r.add(scanner.value());
            }
        }
        return r;
    }

    void check(SegmentRecord rec, int pairIndex, int pid, long interval, boolean status) {
        assertEquals(pairIndex, rec.pairIndex());
        assertEquals(pid, rec.pid());
        assertEquals(PG, rec.pgid());
        assertEquals(interval, rec.segmentStart());
        assertArrayEquals(v(timePid, interval, SegmentKey.TYPE_ENG_VALUE), rec.timeSegment());
        if (pid == timePid) {
            assertNull(rec.engValueSegment());
            assertNull(rec.gaps());
        } else {
            assertArrayEquals(v(pid, interval, SegmentKey.TYPE_ENG_VALUE), rec.engValueSegment());
            assertArrayEquals(v(pid, interval, SegmentKey.TYPE_GAPS), rec.gaps());
        }
        assertNull(rec.rawValueSegment());
        if (status && pid != timePid) {
            assertArrayEquals(v(pid, interval, SegmentKey.TYPE_PARAMETER_STATUS), rec.parameterStatusSegment());
        } else {
            assertNull(rec.parameterStatusSegment());
        }
    }

    @Test
    public void testScanAscending() {
        writeAll();
        var l = scan(TimeEncoding.MIN_INSTANT, TimeEncoding.MAX_INSTANT, true, true, pidA, pidB);
        assertEquals(4, l.size());
        check(l.get(0), 0, pidA, i1, true);
        check(l.get(1), 1, pidB, i2, true);
        check(l.get(2), 0, pidA, i3, true);
        check(l.get(3), 1, pidB, i3, true);
    }

    @Test
    public void testScanDescending() {
        writeAll();
        var l = scan(TimeEncoding.MIN_INSTANT, TimeEncoding.MAX_INSTANT, false, false, pidB, pidA);
        assertEquals(4, l.size());
        check(l.get(0), 0, pidB, i3, false);
        check(l.get(1), 1, pidA, i3, false);
        check(l.get(2), 0, pidB, i2, false);
        check(l.get(3), 1, pidA, i1, false);
    }

    @Test
    public void testScanTimeSegments() {
        writeAll();
        var l = scan(TimeEncoding.MIN_INSTANT, TimeEncoding.MAX_INSTANT, true, true, timePid, pidB);
        assertEquals(5, l.size());
        check(l.get(0), 0, timePid, i1, true);
        check(l.get(1), 0, timePid, i2, true);
        check(l.get(2), 1, pidB, i2, true);
        check(l.get(3), 0, timePid, i3, true);
        check(l.get(4), 1, pidB, i3, true);
    }

    @Test
    public void testScanRange() {
        writeAll();
        // the start is rounded down to the interval start, the stop is inclusive
        var l = scan(i2 + 1000, i3, true, true, pidA, pidB);
        assertEquals(3, l.size());
        check(l.get(0), 1, pidB, i2, true);
        check(l.get(1), 0, pidA, i3, true);
        check(l.get(2), 1, pidB, i3, true);

        l = scan(i1, i2 - 1, false, true, pidA, pidB);
        assertEquals(1, l.size());
        check(l.get(0), 0, pidA, i1, true);

        assertEquals(0, scan(i1, i3, true, true).size());
    }

    @Test
    public void testReopenAndPurge() {
        writeAll();
        storage.saveParameterGroup(PG, IntArray.wrap(pidA, pidB));

        storage = RdbParameterArchiveStorage.open("test1", tablespace, null);
        // the partitioning schema of the existing archive is used
        assertEquals(MONTHLY.getName(), storage.getPartitioningSchema().getName());
        assertEquals(4, scan(TimeEncoding.MIN_INSTANT, TimeEncoding.MAX_INSTANT, true, true, pidA, pidB).size());
        List<IntArray> groups = new ArrayList<>();
        storage.loadParameterGroups((pgid, pids) -> {
            assertEquals(PG, pgid);
            groups.add(pids);
        });
        assertEquals(List.of(IntArray.wrap(pidA, pidB)), groups);

        storage.purge();
        assertEquals(0, storage.listRecords(TablespaceRecord.Type.PARCHIVE_DATA).size());
        assertEquals(0, scan(TimeEncoding.MIN_INSTANT, TimeEncoding.MAX_INSTANT, true, true, pidA, pidB).size());
        storage.loadParameterGroups((pgid, pids) -> {
            throw new AssertionError("no group expected after purge");
        });
    }
}
