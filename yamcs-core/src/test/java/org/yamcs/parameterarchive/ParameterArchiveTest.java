package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.parameterarchive.TestUtils.checkEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

public class ParameterArchiveTest extends BaseParchiveTest {

    static Parameter p1, p2, p3, p4, p5, a1;
 
    @BeforeAll
    public static void beforeClass() {
        p1 = new Parameter("p1");
        p2 = new Parameter("p2");
        p3 = new Parameter("p3");
        p4 = new Parameter("p4");
        p5 = new Parameter("p5");
        a1 = new Parameter("a1");
        p1.setQualifiedName("/test/p1");
        p2.setQualifiedName("/test/p2");
        p3.setQualifiedName("/test/p3");
        p4.setQualifiedName("/test/p4");
        p5.setQualifiedName("/test/p5");

        a1.setQualifiedName("/test/a1");
        TimeEncoding.setUp();

        timeService = new MockupTimeService();
        YamcsServer.setMockupTimeService(timeService);
        // org.yamcs.LoggingUtils.enableLogging();
    }

    @BeforeEach
    public void beforeEach() {
        instance = "ParameterArchiveTest";
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void test1(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        // create a parameter in the map
        int p1id = pidMap.createAndGet("/test/p1", Type.BINARY);

        // close and reopen the archive to check that the parameter is still there

        parchive = new ParameterArchive();
        YConfiguration config = parchive.getSpec().validate(ParameterArchiveTest.backFillerDisabledConfig());
        parchive.init(instance, "test", config);
        pidMap = parchive.getParameterIdDb();
        pgidMap = parchive.getParameterGroupIdDb();
        assertNotNull(pidMap);
        assertNotNull(pgidMap);
        int p2id = pidMap.createAndGet("/test/p2", Type.SINT32);

        assertFalse(p1id == p2id);
        assertEquals(p1id, pidMap.createAndGet("/test/p1", Type.BINARY));
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void testSingleParameter(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        ParameterValue pv1_0 = getParameterValue(p1, 100, "blala100", 100);

        int p1id = pidMap.createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType(),
                pv1_0.getRawValue().getType());

        var pg1 = pgidMap.getGroup(IntArray.wrap(p1id));
        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);

        pgSegment1.addRecord(100, pg1.pids, Arrays.asList(pv1_0));

        ParameterValue pv1_1 = getParameterValue(p1, 200, "blala200", 200);
        pgSegment1.addRecord(200, pg1.pids, Arrays.asList(pv1_1));

        // ascending request on empty data
        List<ParameterValueArray> l0a = retrieveSingleParamSingleGroup(0, 1000, p1id, pg1.id, true);
        assertEquals(0, l0a.size());

        // descending request on empty data
        List<ParameterValueArray> l0d = retrieveSingleParamSingleGroup(0, 1000, p1id, pg1.id, true);
        assertEquals(0, l0d.size());

        parchive.writeToArchive(pgSegment1);

        // ascending request on two value
        List<ParameterValueArray> l4a = retrieveSingleParamSingleGroup(0, TimeEncoding.POSITIVE_INFINITY, p1id, pg1.id,
                true);
        checkEquals(l4a.get(0), pv1_0, pv1_1);

        // descending request on two value
        List<ParameterValueArray> l4d = retrieveSingleParamSingleGroup(0, TimeEncoding.POSITIVE_INFINITY, p1id, pg1.id,
                false);
        checkEquals(l4d.get(0), pv1_1, pv1_0);

        // ascending request on two value with start on first value
        List<ParameterValueArray> l5a = retrieveSingleParamSingleGroup(100, 1000, p1id, pg1.id, true);
        checkEquals(l5a.get(0), pv1_0, pv1_1);

        // descending request on two value with start on second value
        List<ParameterValueArray> l5d = retrieveSingleParamSingleGroup(0, 200, p1id, pg1.id, false);
        checkEquals(l5d.get(0), pv1_1, pv1_0);

        // ascending request on two value with start on the first value and stop on second
        List<ParameterValueArray> l6a = retrieveSingleParamSingleGroup(100, 200, p1id, pg1.id, true);
        checkEquals(l6a.get(0), pv1_0);

        // descending request on two value with start on the second value and stop on first
        List<ParameterValueArray> l6d = retrieveSingleParamSingleGroup(100, 200, p1id, pg1.id, false);
        checkEquals(l6d.get(0), pv1_1);

        // new value in a different interval but same partition
        long t2 = ParameterArchive.getIntervalEnd(0) + 100;
        PGSegment pgSegment2 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t2));
        ParameterValue pv1_2 = getParameterValue(p1, t2, "pv1_2", 30);
        pv1_2.setAcquisitionStatus(AcquisitionStatus.EXPIRED);

        pgSegment2.addRecord(t2, IntArray.wrap(p1id), Arrays.asList(pv1_2));
        parchive.writeToArchive(pgSegment2);

        // new value in a different partition
        long t3 = TimeEncoding.parse("2017-01-01T00:00:51");
        PGSegment pgSegment3 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t3));
        ParameterValue pv1_3 = getParameterValue(p1, t3, "pv1_3", 45);
        pgSegment3.addRecord(t3, IntArray.wrap(p1id), Arrays.asList(pv1_3));
        parchive.writeToArchive(pgSegment3);

        // ascending request on four values
        List<ParameterValueArray> l7a = retrieveSingleParamSingleGroup(0, t3 + 1, p1id, pg1.id, true);
        checkEquals(l7a.get(0), pv1_0, pv1_1);
        checkEquals(l7a.get(1), pv1_2);
        checkEquals(l7a.get(2), pv1_3);

        // descending request on four values
        List<ParameterValueArray> l7d = retrieveSingleParamSingleGroup(0, t3 + 1, p1id, pg1.id, false);
        checkEquals(l7d.get(0), pv1_3);
        checkEquals(l7d.get(1), pv1_2);
        checkEquals(l7d.get(2), pv1_1, pv1_0);

        // ascending request on the last partition
        List<ParameterValueArray> l8a = retrieveSingleParamSingleGroup(t3, t3 + 1, p1id, pg1.id, true);
        checkEquals(l8a.get(0), pv1_3);

        // descending request on the last partition
        List<ParameterValueArray> l8d = retrieveSingleParamSingleGroup(t2, t3, p1id, pg1.id, false);
        checkEquals(l8d.get(0), pv1_3);

        // retrieve only statuses
        List<ParameterValueArray> l9a = retrieveSingleParamSingleGroup(0, t3 + 1, p1id, pg1.id, false, false, false,
                true);
        assertNull(l9a.get(0).engValues);
        assertNull(l9a.get(1).rawValues);
        assertNull(l9a.get(2).engValues);
        assertNotNull(l9a.get(0).paramStatus);
        assertNotNull(l9a.get(1).paramStatus);
        assertNotNull(l9a.get(2).paramStatus);
        assertEquals(AcquisitionStatus.EXPIRED, l9a.get(1).paramStatus[0].getAcquisitionStatus());
        assertEquals(AcquisitionStatus.ACQUIRED, l9a.get(2).paramStatus[0].getAcquisitionStatus());
    }

    /**
     * If raw values are identical with engineering values, the Parameter Archive stores only the engineering values.
     */
    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void testRawEqualsEngParameter(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        ParameterValue pv1_0 = getParameterValue(p1, 100, "blala100", "blala100");
        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType(),
                pv1_0.getRawValue().getType());

        var pg1 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id));
        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);

        pgSegment1.addRecord(100, IntArray.wrap(p1id), Arrays.asList(pv1_0));
        ParameterValue pv1_1 = getParameterValue(p1, 200, "blala200", "blala200");
        pgSegment1.addRecord(200, IntArray.wrap(p1id), Arrays.asList(pv1_1));

        parchive.writeToArchive(pgSegment1);

        Partition p = parchive.getPartitions(100);
        var rdb = parchive.getTablespace().getRdb(p.partitionDir);
        var cfh = rdb.getColumnFamilyHandle(ParameterArchive.CF_NAME);
        assertNotNull(rdb.get(cfh, new SegmentKey(p1id, pg1.id, 0, SegmentKey.TYPE_ENG_VALUE).encode()));
        assertNull(rdb.get(cfh, new SegmentKey(p1id, pg1.id, 0, SegmentKey.TYPE_RAW_VALUE).encode()));

        List<ParameterValueArray> l1a = retrieveSingleParamSingleGroup(0, TimeEncoding.POSITIVE_INFINITY, p1id, pg1.id,
                true, false, true, false);
        checkEquals(false, true, false, l1a.get(0), pv1_0, pv1_1);

        List<ParameterValueArray> l1d = retrieveSingleParamSingleGroup(0, TimeEncoding.POSITIVE_INFINITY, p1id, pg1.id,
                false, false, true, true);
        checkEquals(false, true, true, l1d.get(0), pv1_1, pv1_0);

        List<ParameterIdValueList> params = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id },
                new int[] { pg1.id }, true);
        assertEquals(2, params.size());
        checkEquals(params.get(0), 100, pv1_0);
    }


    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void testSingleParameterMultipleGroups(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        ParameterValue pv1_0 = getParameterValue(p1, 100, "pv1_0");
        ParameterValue pv2_0 = getParameterValue(p2, 100, "pv2_0");
        ParameterValue pv1_1 = getParameterValue(p1, 200, "pv1_1");
        ParameterValue pv1_2 = getParameterValue(p1, 300, "pv1_2");
        pv1_2.setAcquisitionStatus(AcquisitionStatus.INVALID);

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType());
        int p2id = parchive.getParameterIdDb().createAndGet(p2.getQualifiedName(), pv2_0.getEngValue().getType());

        var pg1 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id));
        var pg2 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id));

        // ascending on empty db
        List<ParameterValueArray> l0a = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id, pg2.id }, true);
        assertEquals(0, l0a.size());
        // descending on empty db
        List<ParameterValueArray> l0d = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id, pg2.id }, false);
        assertEquals(0, l0d.size());

        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);
        pgSegment1.addRecord(100, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_0, pv2_0));

        PGSegment pgSegment2 = new PGSegment(pg2.id, 0);
        pgSegment2.addRecord(200, IntArray.wrap(p1id), Arrays.asList(pv1_1));
        pgSegment2.addRecord(300, IntArray.wrap(p1id), Arrays.asList(pv1_2));

        parchive.writeToArchive(0, Arrays.asList(pgSegment1, pgSegment2));

        // ascending on 3 values from same segment
        List<ParameterValueArray> l1a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id, pg2.id }, true);
        assertEquals(1, l1a.size());
        checkEquals(l1a.get(0), pv1_0, pv1_1, pv1_2);

        // descending on 3 values from same segment
        List<ParameterValueArray> l1d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id, pg2.id }, false);
        assertEquals(1, l1d.size());
        checkEquals(l1d.get(0), pv1_2, pv1_1, pv1_0);

        // new value in a different interval but same partition
        long t2 = ParameterArchive.getIntervalEnd(0) + 100;
        PGSegment pgSegment3 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t2));
        ParameterValue pv1_3 = getParameterValue(p1, t2, "pv1_3");
        ParameterValue pv2_1 = getParameterValue(p1, t2, "pv2_1");
        pgSegment3.addRecord(t2, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_3, pv2_1));
        parchive.writeToArchive(pgSegment3);

        // new value in a different partition
        long t3 = TimeEncoding.parse("2017-01-01T00:00:00");
        PGSegment pgSegment4 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t3));
        ParameterValue pv1_4 = getParameterValue(p1, t3, "pv1_4");
        ParameterValue pv2_2 = getParameterValue(p1, t3, "pv2_2");
        pgSegment4.addRecord(t3, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_4, pv2_2));
        parchive.writeToArchive(pgSegment4);

        // ascending on 5 values from three segments ascending
        List<ParameterValueArray> l2a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id, pg2.id }, true);
        assertEquals(3, l2a.size());
        checkEquals(l2a.get(0), pv1_0, pv1_1, pv1_2);
        checkEquals(l2a.get(1), pv1_3);
        checkEquals(l2a.get(2), pv1_4);

        // descending on 3 values from three segments descending
        List<ParameterValueArray> l2d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id, pg2.id }, false);
        assertEquals(3, l2d.size());
        checkEquals(l2d.get(0), pv1_4);
        checkEquals(l2d.get(1), pv1_3);
        checkEquals(l2d.get(2), pv1_2, pv1_1, pv1_0);

        // partial retrieval from first segment ascending
        List<ParameterValueArray> l3a = retrieveSingleValueMultigroup(101, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id, pg2.id }, true);
        assertEquals(3, l3a.size());
        checkEquals(l3a.get(0), pv1_1, pv1_2);
        checkEquals(l3a.get(1), pv1_3);
        checkEquals(l3a.get(2), pv1_4);

        // partial retrieval from first segment descending
        List<ParameterValueArray> l3d = retrieveSingleValueMultigroup(101, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id, pg2.id }, false);
        assertEquals(3, l3d.size());
        checkEquals(l3d.get(0), pv1_4);
        checkEquals(l3d.get(1), pv1_3);
        checkEquals(l3d.get(2), pv1_2, pv1_1);

        // ascending with empty request inside existing segment
        List<ParameterValueArray> l4a = retrieveSingleValueMultigroup(101, 102, p1id, new int[] { pg1.id, pg2.id },
                true);
        assertEquals(0, l4a.size());

        // descending with empty request inside existing segment
        List<ParameterValueArray> l4d = retrieveSingleValueMultigroup(101, 102, p1id, new int[] { pg1.id, pg2.id },
                false);
        assertEquals(0, l4d.size());

        // retrieve only statuses
        List<ParameterValueArray> l5a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id, pg2.id }, true, false, false, true);
        assertNull(l5a.get(0).engValues);
        assertNull(l5a.get(1).rawValues);
        assertNull(l5a.get(2).engValues);
        assertNotNull(l5a.get(0).paramStatus);
        assertNotNull(l5a.get(1).paramStatus);
        assertNotNull(l5a.get(2).paramStatus);

        assertEquals(AcquisitionStatus.INVALID, l5a.get(0).paramStatus[2].getAcquisitionStatus());
        assertEquals(AcquisitionStatus.ACQUIRED, l5a.get(2).paramStatus[0].getAcquisitionStatus());

    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void testMultipleParameters(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        ParameterValue pv1_0 = getParameterValue(p1, 100, "pv1_0");
        ParameterValue pv2_0 = getParameterValue(p2, 100, "pv2_0");
        ParameterValue pv1_1 = getParameterValue(p1, 200, "pv1_1");
        ParameterValue pv1_2 = getParameterValue(p1, 300, "pv1_2");

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType());
        int p2id = parchive.getParameterIdDb().createAndGet(p2.getQualifiedName(), pv2_0.getEngValue().getType());

        var pg1 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id));
        var pg2 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id));

        // ascending on empty db
        List<ParameterIdValueList> l0a = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id }, new int[] { pg1.id, pg1.id }, true);
        assertEquals(0, l0a.size());

        // descending on empty db
        List<ParameterIdValueList> l0d = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id }, new int[] { pg1.id, pg1.id }, false);
        assertEquals(0, l0d.size());

        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);
        pgSegment1.addRecord(100, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_0, pv2_0));

        PGSegment pgSegment2 = new PGSegment(pg2.id, 0);
        pgSegment2.addRecord(200, IntArray.wrap(p1id), Arrays.asList(pv1_1));
        pgSegment2.addRecord(300, IntArray.wrap(p1id), Arrays.asList(pv1_2));

        parchive.writeToArchive(0, Arrays.asList(pgSegment1, pgSegment2));

        // ascending, retrieving one parameter from he group of two
        List<ParameterIdValueList> l1a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg1.id }, true);
        assertEquals(1, l1a.size());
        checkEquals(l1a.get(0), 100, pv1_0);

        // descending, retrieving one parameter from the group of two
        List<ParameterIdValueList> l1d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg1.id }, false);
        assertEquals(1, l1d.size());
        checkEquals(l1d.get(0), 100, pv1_0);

        // ascending, retrieving one para from the group of one
        List<ParameterIdValueList> l2a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg2.id }, true);
        assertEquals(2, l2a.size());
        checkEquals(l2a.get(0), 200, pv1_1);
        checkEquals(l2a.get(1), 300, pv1_2);

        // descending, retrieving one para from the group of one
        List<ParameterIdValueList> l2d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg2.id }, false);
        assertEquals(2, l2d.size());
        checkEquals(l2d.get(0), 300, pv1_2);
        checkEquals(l2d.get(1), 200, pv1_1);

        // ascending retrieving two para
        List<ParameterIdValueList> l3a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1.id, pg1.id }, true);

        assertEquals(1, l3a.size());
        checkEquals(l3a.get(0), 100, pv1_0, pv2_0);

        // descending retrieving two para
        List<ParameterIdValueList> l3d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p2id, p1id }, new int[] { pg1.id, pg1.id }, false);
        assertEquals(1, l3d.size());
        checkEquals(l3d.get(0), 100, pv1_0, pv2_0);

        // new value in a different interval but same partition
        long t2 = ParameterArchive.getIntervalEnd(0) + 100;
        PGSegment pgSegment3 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t2));
        ParameterValue pv1_3 = getParameterValue(p1, t2, "pv1_3");
        ParameterValue pv2_1 = getParameterValue(p1, t2, "pv2_1");
        pgSegment3.addRecord(t2, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_3, pv2_1));
        parchive.writeToArchive(pgSegment3);

        // new value in a different partition
        long t3 = TimeEncoding.parse("2017-01-01T00:00:00");
        PGSegment pgSegment4 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t3));
        ParameterValue pv1_4 = getParameterValue(p1, t3, "pv1_4");
        ParameterValue pv2_2 = getParameterValue(p1, t3, "pv2_2");
        pgSegment4.addRecord(t3, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_4, pv2_2));
        parchive.writeToArchive(pgSegment4);

        // ascending retrieving two para
        List<ParameterIdValueList> l4a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1.id, pg1.id }, true);
        assertEquals(3, l4a.size());
        checkEquals(l4a.get(0), 100, pv1_0, pv2_0);
        checkEquals(l4a.get(1), t2, pv1_3, pv2_1);
        checkEquals(l4a.get(2), t3, pv1_4, pv2_2);

        // descending retrieving two para
        List<ParameterIdValueList> l4d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p2id, p1id }, new int[] { pg1.id, pg1.id }, false);
        assertEquals(3, l4d.size());
        checkEquals(l4d.get(0), t3, pv1_4, pv2_2);
        checkEquals(l4d.get(1), t2, pv1_3, pv2_1);
        checkEquals(l4d.get(2), 100, pv1_0, pv2_0);

        // ascending with empty request inside existing segment
        List<ParameterIdValueList> l5a = retrieveMultipleParameters(101, 102, new int[] { p1id, p2id },
                new int[] { pg1.id, pg1.id }, true);
        assertEquals(0, l5a.size());

        // descending with empty request inside existing segment
        List<ParameterIdValueList> l5d = retrieveMultipleParameters(101, 102, new int[] { p2id, p1id },
                new int[] { pg1.id, pg1.id }, false);
        assertEquals(0, l5d.size());

        // ascending retrieving two para limited time interval
        List<ParameterIdValueList> l6a = retrieveMultipleParameters(t2, t2 + 1, new int[] { p1id, p2id },
                new int[] { pg1.id, pg1.id }, true);
        assertEquals(1, l6a.size());
        checkEquals(l6a.get(0), t2, pv1_3, pv2_1);

        // descending retrieving two para limited time interval
        List<ParameterIdValueList> l6d = retrieveMultipleParameters(t2 - 1, t2, new int[] { p2id, p1id },
                new int[] { pg1.id, pg1.id }, false);
        assertEquals(1, l6d.size());
        checkEquals(l6d.get(0), t2, pv1_3, pv2_1);

        // ascending retrieving two para with limit 2
        List<ParameterIdValueList> l7a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1.id, pg1.id }, true, 2);
        assertEquals(2, l7a.size());
        checkEquals(l7a.get(0), 100, pv1_0, pv2_0);
        checkEquals(l7a.get(1), t2, pv1_3, pv2_1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void testArrays(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        ParameterValue pva1_0 = getArrayValue(a1, 100, "a");
        ParameterValue pva1_1 = getArrayValue(a1, 100, "b", "c");

        long t1 = TimeEncoding.parse("2021-06-02T00:00:00");
        long t2 = TimeEncoding.parse("2021-06-02T00:10:00");

        BasicParameterList l1 = new BasicParameterList(parchive.getParameterIdDb());
        l1.add(pva1_0);

        BasicParameterList l2 = new BasicParameterList(parchive.getParameterIdDb());
        l2.add(pva1_1);

        var pg1 = parchive.getParameterGroupIdDb().getGroup(l1.getPids());
        var pg2 = parchive.getParameterGroupIdDb().getGroup(l2.getPids());

        // because sparseGroups is false, the different size array values are stored in different groups
        assertTrue(pg2.id != pg1.id);

        PGSegment pgSegment2 = new PGSegment(pg2.id, ParameterArchive.getInterval(t2));
        pgSegment2.addRecord(t2, l2.getPids(), l2.getValues());

        PGSegment pgSegment1 = new PGSegment(pg1.id, ParameterArchive.getInterval(t1));

        pgSegment1.addRecord(t1, l1.getPids(), l1.getValues());

        parchive.writeToArchive(pgSegment2);
        parchive.writeToArchive(pgSegment1);

        ParameterId[] parameterIds = parchive.getParameterIdDb().get(a1.getQualifiedName());
        assertEquals(1, parameterIds.length);

        MultipleParameterRequest mpvr = new MultipleParameterRequest(0l, TimeEncoding.MAX_INSTANT,
                parameterIds, true);

        MultiParameterRetrieval mpdr = new MultiParameterRetrieval(parchive, mpvr);
        MultiValueConsumer c = new MultiValueConsumer();
        mpdr.retrieve(c);
        assertEquals(2, c.list.size());
        ParameterIdValueList rl0 = c.list.get(0);
        assertEquals(1, rl0.size());
        checkEquals(c.list.get(0), t1, pva1_0);

        checkEquals(c.list.get(1), t2, pva1_1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void testExpireMillis(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        long t = TimeEncoding.parse("2018-03-19T10:35:00");
        ParameterValue pv1_0 = getParameterValue(p1, t, "blala" + t, (int) t);
        pv1_0.setExpireMillis(1234);

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType(),
                pv1_0.getRawValue().getType());

        var pg1 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id));
        PGSegment pgSegment1 = new PGSegment(pg1.id, ParameterArchive.getInterval(t));

        pgSegment1.addRecord(t, IntArray.wrap(p1id), Arrays.asList(pv1_0));

        parchive.writeToArchive(pgSegment1);

        // ascending request on empty data
        List<ParameterValueArray> l0a = retrieveSingleParamSingleGroup(t, t + 1, p1id, pg1.id, true);
        Pvalue.ParameterStatus pstatus = l0a.get(0).paramStatus[0];
        assertTrue(pstatus.hasExpireMillis());
        assertEquals(1234, pstatus.getExpireMillis());
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "YYYY", "YYYY/MM" })
    public void testSameTimestamp(String partitioningSchema) throws Exception {
        openDb(partitioningSchema);

        ParameterValue pv1_0 = getParameterValue(p1, 100, "blala100", 100);

        int p1id = pidMap.createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType(),
                pv1_0.getRawValue().getType());

        var pg1 = pgidMap.getGroup(IntArray.wrap(p1id));
        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);

        pgSegment1.addRecord(100, IntArray.wrap(p1id), Arrays.asList(pv1_0));
        ParameterValue pv1_1 = getParameterValue(p1, 100, "blala200", 200);
        pgSegment1.addRecord(100, IntArray.wrap(p1id), Arrays.asList(pv1_1));

        parchive.writeToArchive(pgSegment1);

        List<ParameterValueArray> l1a = retrieveSingleParamSingleGroup(0, TimeEncoding.POSITIVE_INFINITY, p1id, pg1.id,
                true);
        checkEquals(l1a.get(0), pv1_0, pv1_1);

        List<ParameterIdValueList> l2a = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id },
                new int[] { pg1.id }, true);
        assertEquals(1, l1a.size());
        checkEquals(l2a.get(0), 100, pv1_0, pv1_1);
    }


    public static YConfiguration backFillerDisabledConfig() {
        Map<String, Object> pam = new HashMap<>();
        Map<String, Object> bfm = new HashMap<>();
        bfm.put("enabled", Boolean.FALSE);
        pam.put("backFiller", bfm);
        return YConfiguration.wrap(pam);
    }



}
