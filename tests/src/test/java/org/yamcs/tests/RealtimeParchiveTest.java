package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.client.Page;
import org.yamcs.client.archive.ArchiveClient.ListOptions;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.util.Timestamps;

/**
 * Tests with the realtime parameter archive filler
 */
public class RealtimeParchiveTest extends AbstractIntegrationTest {
    Mdb mdb;

    @BeforeAll
    public static void beforeClass() throws Exception {
        setupYamcs("RealtimeParchive", true);
    }

    @Test
    public void test1() throws Exception {
        generatePkt13AndPps("2024-07-23T06:00:00", 3600);

        Instant start = Instant.parse("2024-07-23T06:59:00Z");
        Instant stop = Instant.parse("2024-07-23T07:01:00Z");

        var archiveClient = yamcsClient.createArchiveClient(yamcsInstance);

        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                ListOptions.ascending(true), ListOptions.limit(500)).get();
        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(60, values.size());

        yamcs.shutDown();
        setupYamcs("RealtimeParchive", false);

        before();
        generatePkt13AndPps("2024-07-23T07:00:00", 3600);

        archiveClient = yamcsClient.createArchiveClient(yamcsInstance);

        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                ListOptions.ascending(true), ListOptions.limit(500)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(120, values.size());
        assertEquals(Timestamps.parse("2024-07-23T06:59:00Z"), values.get(0).getGenerationTime());
        assertEquals(Timestamps.parse("2024-07-23T07:00:00Z"), values.get(60).getGenerationTime());
        assertEquals(Timestamps.parse("2024-07-23T07:00:59Z"), values.get(119).getGenerationTime());

        yamcs.shutDown();
        setupYamcs("RealtimeParchive", false);

        before();
        generatePkt13AndPps("2024-07-23T07:00:00.100", 1);

        // if we perform now the retrieval, we only get 120 records because the SegmentIterator only looks at realtime
        // filler data after has read the data from the archive and does not expect data that is already written in the
        // archive to be modified
        yamcs.shutDown();
        setupYamcs("RealtimeParchive", false);

        archiveClient = yamcsClient.createArchiveClient(yamcsInstance);

        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                ListOptions.ascending(true), ListOptions.limit(500)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(121, values.size());
        assertEquals(Timestamps.parse("2024-07-23T06:59:00Z"), values.get(0).getGenerationTime());
        assertEquals(Timestamps.parse("2024-07-23T07:00:00Z"), values.get(60).getGenerationTime());
        assertEquals(Timestamps.parse("2024-07-23T07:00:00.100Z"), values.get(61).getGenerationTime());
        assertEquals(Timestamps.parse("2024-07-23T07:00:59Z"), values.get(120).getGenerationTime());

    }

    @Test
    public void test2() throws Exception {
        mdb = MdbFactory.getInstance(yamcsInstance);
        // t0 and t1 are two different intervals
        var t0 = TimeEncoding.parse("2025-03-04T16:00:00Z");
        var t1 = TimeEncoding.parse("2025-03-04T19:00:00Z");

        inject(t0, true, true);
        inject(t1, true, false);
        yamcs.shutDown();
        setupYamcs("RealtimeParchive", false);
        before();
        inject(t1 + 10, true, true);

        yamcs.shutDown();
        setupYamcs("RealtimeParchive", false);
        before();

        var archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
        var page = archiveClient.listValues("/REFMDB/SUBSYS1/processed_para_string",
                Instant.parse("2025-03-04T16:00:00Z"), Instant.parse("2025-03-04T20:00:00Z"),
                ListOptions.ascending(true)).get();
        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(2, values.size());
        assertEquals(Timestamps.parse("2025-03-04T16:00:00Z"), values.get(0).getGenerationTime());
        assertEquals(Timestamps.parse("2025-03-04T19:00:00.010Z"), values.get(1).getGenerationTime());
    }

    void inject(long t, boolean p1, boolean p2) {
        List<org.yamcs.parameter.ParameterValue> pvList = new ArrayList<>();
        if (p1) {
            org.yamcs.parameter.ParameterValue pv1 = new org.yamcs.parameter.ParameterValue(
                    mdb.getParameter("/REFMDB/SUBSYS1/processed_para_uint"));
            pv1.setUnsignedIntegerValue(3);
            pv1.setGenerationTime(t);
            pvList.add(pv1);
        }
        if (p2) {
            org.yamcs.parameter.ParameterValue pv2 = new org.yamcs.parameter.ParameterValue(
                    mdb.getParameter("/REFMDB/SUBSYS1/processed_para_string"));
            pv2.setGenerationTime(t);
            pv2.setStringValue("para" + t);
            pvList.add(pv2);
        }
        parameterProvider.inject(t, pvList);
    }
}
