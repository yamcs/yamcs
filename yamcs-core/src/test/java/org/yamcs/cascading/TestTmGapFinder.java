package org.yamcs.cascading;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.cascading.YamcsTmArchiveLink.Gap;
import org.yamcs.cascading.TmGapFinder.GapCollector;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Timestamp;

public class TestTmGapFinder {

    @BeforeAll
    public static void beforeAll() {
        TimeEncoding.setUp();
    }

    @Test
    void testGapCollector() {
        GapCollector collector = new GapCollector();
        collector.addGap(10, 20);
        assertEquals(1, collector.gaps.size());
        collector.addGap(20, 40);
        assertEquals(1, collector.gaps.size());
        checkEquals(10, 40, collector.gaps.get(0));

        collector.addGap(50, 100);
        assertEquals(2, collector.gaps.size());
        checkEquals(10, 40, collector.gaps.get(0));
        checkEquals(50, 100, collector.gaps.get(1));

        collector.addGap(0, 20);
        assertEquals(2, collector.gaps.size());
        checkEquals(0, 40, collector.gaps.get(0));
        checkEquals(50, 100, collector.gaps.get(1));

        collector.addGap(30, 50);
        assertEquals(1, collector.gaps.size());
        checkEquals(0, 100, collector.gaps.get(0));

        collector.addGap(0, 50);
        assertEquals(1, collector.gaps.size());
        checkEquals(0, 100, collector.gaps.get(0));

        collector.addGap(20, 100);
        assertEquals(1, collector.gaps.size());
        checkEquals(0, 100, collector.gaps.get(0));

    }

    @Test
    void testDiff() {
        GapCollector collector = new GapCollector();
        MyLog log = new MyLog();
        List<ArchiveRecord> upstreamRecords = getRecords(
                10, 20,
                40, 50,
                60, 70,
                80, 90,
                100, 110,
                150, 160); // 4rth gap

        List<ArchiveRecord> downstreamRecords = getRecords(
                2, 7, // 1st warning
                10, 20,
                35, 55, // 2nd warning
                65, 68, // 1st gap
                80, 85, // 2nd gap
                105, 115, // 3rd warning, 3rd gap
                130, 140, // 4th warning
                180, 190); // 5th warning

        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        assertEquals(4, collector.gaps.size());
        assertEquals(5, log.warns.size());
        checkEquals_s(60, 70, collector.gaps.get(0));
        checkEquals_s(80, 90, collector.gaps.get(1));
        checkEquals_s(100, 110, collector.gaps.get(2));
        checkEquals_s(150, 160, collector.gaps.get(3));

    }

    @Test
    void testDiffSingle() {
        /* Conditions (in same order as code):
        1.  Down fully included in up (gaps before and after if larger, skip both)
        2.  Down completely after up (gap = entire up, skip up)
        3.  Down completely before up (skip down + warn if previous up in part before down w/o including)
        4.  Up fully included in down (skip up + warn)
        5.1 Down overlaps up w/o inclusion (before, gap = up, skip both)
        5.2 Down overlaps up w/o inclusion (after, gap = up, skip both)
        */
        GapCollector collector = new GapCollector();
        MyLog log = new MyLog();

        // 1
        List<ArchiveRecord> upstreamRecords = getRecords(
                100, 110, // no gap
                125, 140, // gap before
                150, 165, // gap after
                170, 190, // both gaps
                200, 210 // gap

        );
        List<ArchiveRecord> downstreamRecords = getRecords(
                100, 110, // no gap
                130, 140, // gap before
                150, 160, // gap after
                175, 185  // both gaps
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        // 2
        upstreamRecords = getRecords(
                200, 210 // gap (+ warn end)
        );
        downstreamRecords = getRecords(
                220, 230 // gap (+ warn end)
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);
        upstreamRecords = getRecords(
                240, 250 // gap w/ last value overlap (+ warn end)
        );
        downstreamRecords = getRecords(
                250, 260 // gap w/ last value overlap (+ warn end)
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        // 3
        upstreamRecords = getRecords(
                320, 330 // gap at end (downstream exhausted + warn beginning)
        );
        downstreamRecords = getRecords(
                300, 310 // gap at end (downstream exhausted + warn beginning)
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);
        upstreamRecords = getRecords(
                350, 360 // gap at end w/ last value overlap (downstream exhausted + warn beginning)
        );
        downstreamRecords = getRecords(
                340, 350 // gap at end w/ last value overlap (downstream exhausted + warn beginning)
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        // 4
        upstreamRecords = getRecords(
                410, 420 // no gap (warn)
        );
        downstreamRecords = getRecords(
                400, 430 // no gap (warn)
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        // 5
        upstreamRecords = getRecords(
                510, 530, // 5.1 gap (warn)
                540, 560 // 5.2 gap (warn)
        );
        downstreamRecords = getRecords(
                500, 520, // 5.1 gap (warn)
                550, 570 // 5.2 gap (warn)
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        assertEquals(9, collector.gaps.size());
        assertEquals(7, log.warns.size());
        checkEquals_s(125, 140, collector.gaps.get(0));
        checkEquals_s(150, 165, collector.gaps.get(1));
        checkEquals_s(170, 190, collector.gaps.get(2));
        checkEquals_s(200, 210, collector.gaps.get(3));
        checkEquals_s(240, 250, collector.gaps.get(4));
        checkEquals_s(320, 330, collector.gaps.get(5));
        checkEquals_s(350, 360, collector.gaps.get(6));
        checkEquals_s(510, 530, collector.gaps.get(7));
        checkEquals_s(540, 560, collector.gaps.get(8));
    }

    @Test
    void testDiffCoupled() {
        // All conditions can lead to others, except 4th -> 3, 4, 5 (up after down)
        // Shortest sequence to all possible conditions
        // 1 -> 5.2 -> 5.2 -> 5.1 -> 5.1 -> 5.2 -> 4 -> 3 -> 5.2 -> 3 -> 2 -> 5.2 -> 2 -> 5.1 -> 4 -> 3 -> 1 -> 5.1 -> 2
        // -> 4 -> 3 -> 5.1 -> 3 -> 5.2 -> 1 -> 4 -> 3 -> 2 -> 2 -> 3 -> 3 -> 4 -> 4 -> 5.1 -> 1 -> 3 -> 1 -> 2 -> 1 -> 1
        GapCollector collector = new GapCollector();
        MyLog log = new MyLog();

        List<ArchiveRecord> upstreamRecords = getRecords(
                1, 4,
                5, 7,
                9, 11,
                14, 16,
                18, 20,
                21, 23,
                26, 27,
                29, 31,
                35, 36,
                37, 39,
                41, 42,
                44, 46,
                48, 49,
                51, 54,
                56, 58,
                59, 60,
                62, 63,
                66, 68,
                71, 73,
                75, 78,
                80, 81,
                83, 84,
                85, 86,
                92, 93,
                94, 95,
                96, 98,
                99, 102,
                105, 108,
                109, 110,
                111, 114,
                115, 118
        );
        List<ArchiveRecord> downstreamRecords = getRecords(
                2, 3,
                6, 8,
                10, 12,
                13, 15,
                17, 19,
                22, 24,
                25, 28,
                30, 32,
                33, 34,
                38, 40,
                43, 45,
                47, 50,
                52, 53,
                55, 57,
                61, 64,
                65, 67,
                69, 70,
                72, 74,
                76, 77,
                79, 82,
                87, 88,
                89, 90,
                91, 97,
                100, 101,
                103, 104,
                106, 107,
                112, 113,
                116, 117
        );

        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        assertEquals(25, collector.gaps.size()); // Number of 1, 2, 5.1 and 5.2 in sequence
        assertEquals(23, log.warns.size()); // Number of 3 (preceded by 2 and 4), 4, 5.1 and 5.2 in sequence
    }

    @Test
    void testDiffEdgeCases() {
        GapCollector collector = new GapCollector();
        MyLog log = new MyLog();

        // Downstream longer than upstream but still covered by upstream
        List<ArchiveRecord> upstreamRecords = getRecords(
                1, 4,
                5, 10 // gap
        );
        List<ArchiveRecord> downstreamRecords = getRecords(
                1, 4,
                5, 6, // gap
                7, 8 // no warning
        );
        TmGapFinder.addMissing(log, collector, upstreamRecords, downstreamRecords);

        assertEquals(1, collector.gaps.size());
        assertEquals(0, log.warns.size());
        checkEquals_s(5, 10, collector.gaps.get(0));
        // TODO: more cases
    }

    private List<ArchiveRecord> getRecords(long... t) {
        List<ArchiveRecord> r = new ArrayList<>();
        for (int i = 0; i < t.length; i += 2) {
            r.add(getRecord(t[i], t[i + 1]));
        }
        return r;
    }

    private ArchiveRecord getRecord(long first, long last) {
        return ArchiveRecord.newBuilder().setFirst(getTimestamp(first)).setLast(getTimestamp(last))
                .setNum((int) (last - first + 1)).build();
    }

    private Timestamp getTimestamp(long t) {
        return Timestamp.newBuilder().setSeconds(t).setNanos(0).build();
    }

    private void checkEquals(long start, long stop, Gap gap) {
        assertEquals(start, gap.start);
        assertEquals(stop, gap.stop);
    }

    private void checkEquals_s(long start, long stop, Gap gap) {
        checkEquals(1000 * start, 1000 * stop, gap);
    }
    static class MyLog extends Log {
        List<String> warns = new ArrayList<>();

        public MyLog() {
            super(TestTmGapFinder.class);
        }

        public void warn(String msg) {
//            System.out.println(msg);
            warns.add(msg);
        }
    }
}
