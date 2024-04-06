package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.parameterarchive.TestUtils.checkEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

public class ParchiveWithSparseGroupsTest extends BaseParchiveTest {

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
        instance = "ParchiveWithSparseGroupsTest";
    }


    @AfterEach
    public void closeDb() throws Exception {
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        rse.dropTablespace(instance);
    }

    @Test
    public void test1() throws Exception {
        openDb("none", true, 0.5);
        ParameterValue pv1_0 = getParameterValue(p1, 100, "pv1_0");
        ParameterValue pv2_0 = getParameterValue(p2, 100, "pv2_0");
        ParameterValue pv1_1 = getParameterValue(p1, 200, "pv1_1");
        ParameterValue pv1_2 = getParameterValue(p1, 300, "pv1_2");
        pv1_2.setAcquisitionStatus(AcquisitionStatus.INVALID);

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType());
        int p2id = parchive.getParameterIdDb().createAndGet(p2.getQualifiedName(), pv2_0.getEngValue().getType());

        var pg1 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id));
        var pg2 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id));

        assertEquals(pg1.id, pg2.id);

        // ascending on empty db
        List<ParameterValueArray> l0a = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id }, true);
        assertEquals(0, l0a.size());
        // descending on empty db
        List<ParameterValueArray> l0d = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id }, false);
        assertEquals(0, l0d.size());

        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);
        pgSegment1.addRecord(100, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_0, pv2_0));
        pgSegment1.addRecord(200, IntArray.wrap(p1id), Arrays.asList(pv1_1));
        pgSegment1.addRecord(300, IntArray.wrap(p1id), Arrays.asList(pv1_2));

        parchive.writeToArchive(0, Arrays.asList(pgSegment1));

        List<ParameterValueArray> l1a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, true);
        assertEquals(1, l1a.size());
        checkEquals(l1a.get(0), pv1_0, pv1_1, pv1_2);

        List<ParameterValueArray> l1d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, false);
        assertEquals(1, l1d.size());
        checkEquals(l1d.get(0), pv1_2, pv1_1, pv1_0);


        List<ParameterValueArray> l2a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p2id,
                new int[] { pg1.id }, true);
        assertEquals(1, l2a.size());
        checkEquals(l2a.get(0), pv2_0);

        List<ParameterValueArray> l2d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p2id,
                new int[] { pg1.id }, false);
        assertEquals(1, l2d.size());
        checkEquals(l2d.get(0), pv2_0);

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
        ParameterValue pv2_2 = getParameterValue(p2, t3, "pv2_2");
        pgSegment4.addRecord(t3, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_4, pv2_2));
        parchive.writeToArchive(pgSegment4);

        // new segment with only p2
        long t4 = TimeEncoding.parse("2017-01-01T00:00:00");
        PGSegment pgSegment5 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t3));
        ParameterValue pv2_3 = getParameterValue(p2, t3, "pv2_2");
        pgSegment5.addRecord(t4, IntArray.wrap(p2id), Arrays.asList(pv2_3));
        parchive.writeToArchive(pgSegment5);

        // p1 ascending on 5 values from three segments (fourth one is empty)
        List<ParameterValueArray> l3a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, true);
        assertEquals(3, l3a.size());
        checkEquals(l3a.get(0), pv1_0, pv1_1, pv1_2);
        checkEquals(l3a.get(1), pv1_3);
        checkEquals(l3a.get(2), pv1_4);

        // p1 descending on 3 values from three segments (fourth one is empty)
        List<ParameterValueArray> l3d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, false);
        assertEquals(3, l3d.size());
        checkEquals(l3d.get(0), pv1_4);
        checkEquals(l3d.get(1), pv1_3);
        checkEquals(l3d.get(2), pv1_2, pv1_1, pv1_0);

        // p2 ascending on 5 values from four segments
        List<ParameterValueArray> l4a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p2id,
                new int[] { pg1.id }, true);
        assertEquals(3, l4a.size());
        checkEquals(l4a.get(0), pv2_0);
        checkEquals(l4a.get(1), pv2_1);
        checkEquals(l4a.get(2), pv2_2);

        // p2 ascending on 5 values from four segments
        List<ParameterValueArray> l4d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p2id,
                new int[] { pg1.id }, false);
        assertEquals(3, l4d.size());
        checkEquals(l4d.get(0), pv2_2);
        checkEquals(l4d.get(1), pv2_1);
        checkEquals(l4d.get(2), pv2_0);

        // p1 partial retrieval from first segment ascending
        List<ParameterValueArray> l5a = retrieveSingleValueMultigroup(101, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, true);
        assertEquals(3, l5a.size());
        checkEquals(l5a.get(0), pv1_1, pv1_2);
        checkEquals(l5a.get(1), pv1_3);
        checkEquals(l5a.get(2), pv1_4);

        // p1 partial retrieval from the first segment descending
        List<ParameterValueArray> l5d = retrieveSingleValueMultigroup(101, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, false);
        assertEquals(3, l5d.size());
        checkEquals(l5d.get(0), pv1_4);
        checkEquals(l5d.get(1), pv1_3);
        checkEquals(l5d.get(2), pv1_2, pv1_1);

        // ascending with empty request inside existing segment
        List<ParameterValueArray> l6a = retrieveSingleValueMultigroup(101, 102, p1id, new int[] { pg1.id, pg2.id },
                true);
        assertEquals(0, l6a.size());

        // descending with empty request inside existing segment
        List<ParameterValueArray> l6d = retrieveSingleValueMultigroup(101, 102, p1id, new int[] { pg1.id, pg2.id },
                false);
        assertEquals(0, l6d.size());

        // retrieve only statuses
        List<ParameterValueArray> l7a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, true, false, false, true);
        assertNull(l7a.get(0).engValues);
        assertNull(l7a.get(1).rawValues);
        assertNull(l7a.get(2).engValues);
        assertNotNull(l7a.get(0).paramStatus);
        assertNotNull(l7a.get(1).paramStatus);
        assertNotNull(l7a.get(2).paramStatus);

        assertEquals(AcquisitionStatus.INVALID, l7a.get(0).paramStatus[2].getAcquisitionStatus());
        assertEquals(AcquisitionStatus.ACQUIRED, l7a.get(2).paramStatus[0].getAcquisitionStatus());
    }

    @Test
    public void test2() throws Exception {
        openDb("none", true, 1);
        ParameterValue pv1_0 = getParameterValue(p1, 100, "pv1_0");
        ParameterValue pv3_0 = getParameterValue(p3, 100, "pv1_2");

        ParameterValue pv2_0 = getParameterValue(p2, 200, "pv2_0");
        ParameterValue pv3_1 = getParameterValue(p3, 200, "pv3_1");

        ParameterValue pv3_2 = getParameterValue(p3, 300, "pv3_2");

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType());
        int p2id = parchive.getParameterIdDb().createAndGet(p2.getQualifiedName(), pv2_0.getEngValue().getType());
        int p3id = parchive.getParameterIdDb().createAndGet(p3.getQualifiedName(), pv3_0.getEngValue().getType());

        var pg1 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p3id));
        var pg2 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p2id, p3id));
        var pg3 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p3id));

        assertNotEquals(pg1.id, pg2.id);
        assertEquals(pg1.id, pg3.id);

        // ascending on empty db
        List<ParameterValueArray> l0a = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id, pg2.id }, true);
        assertEquals(0, l0a.size());
        // descending on empty db
        List<ParameterValueArray> l0d = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id, pg2.id }, false);
        assertEquals(0, l0d.size());

        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);
        PGSegment pgSegment2 = new PGSegment(pg2.id, 0);

        pgSegment1.addRecord(100, IntArray.wrap(p1id, p3id), Arrays.asList(pv1_0, pv3_0));
        pgSegment2.addRecord(200, IntArray.wrap(p2id, p3id), Arrays.asList(pv2_0, pv3_1));
        pgSegment1.addRecord(300, IntArray.wrap(p3id), Arrays.asList(pv3_2));

        parchive.writeToArchive(pgSegment1);
        parchive.writeToArchive(pgSegment2);

        List<ParameterValueArray> l1a = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p3id,
                new int[] { pg1.id, pg2.id }, true);

        assertEquals(1, l1a.size());
        checkEquals(l1a.get(0), pv3_0, pv3_1, pv3_2);

        List<ParameterValueArray> l1d = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p3id,
                new int[] { pg1.id, pg2.id }, false);
        assertEquals(1, l1d.size());
        checkEquals(l1a.get(0), pv3_0, pv3_1, pv3_2);


        List<ParameterValueArray> l2a = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id }, true);
        assertEquals(1, l2a.size());
        checkEquals(l2a.get(0), pv1_0);

        List<ParameterValueArray> l2d = retrieveSingleValueMultigroup(0, TimeEncoding.POSITIVE_INFINITY, p1id,
                new int[] { pg1.id }, false);
        assertEquals(1, l2d.size());
        checkEquals(l2a.get(0), pv1_0);

        System.out.println("multi retrieval");
        List<ParameterIdValueList> l4a = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id, p3id, p3id }, new int[] { pg1.id, pg2.id, pg1.id, pg2.id }, true);
        assertEquals(3, l4a.size());
        checkEquals(l4a.get(0), 100, pv1_0, pv3_0);
        checkEquals(l4a.get(1), 200, pv2_0, pv3_1);
        checkEquals(l4a.get(2), 300, pv3_2);

        List<ParameterIdValueList> l4d = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id, p3id, p3id }, new int[] { pg1.id, pg2.id, pg1.id, pg2.id }, false);
        assertEquals(3, l4a.size());
        checkEquals(l4d.get(0), 300, pv3_2);
        checkEquals(l4d.get(1), 200, pv2_0, pv3_1);
        checkEquals(l4d.get(2), 100, pv1_0, pv3_0);

    }

    @Test
    public void testArrays() throws Exception {
        openDb("none", true, 0.5);

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

        // because sparseGroups is true, the different size array values are stored in the same group
        assertTrue(pg2.id == pg1.id);

        PGSegment pgSegment1 = new PGSegment(pg1.id, ParameterArchive.getIntervalStart(t2));
        pgSegment1.addRecord(t2, l2.getPids(), l2.getValues());
        pgSegment1.addRecord(t1, l1.getPids(), l1.getValues());

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
}
