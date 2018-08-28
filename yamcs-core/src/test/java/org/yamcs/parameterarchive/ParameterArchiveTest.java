package org.yamcs.parameterarchive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.yamcs.parameterarchive.TestUtils.checkEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.rocksdb.RocksDBException;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.parameterarchive.ParameterArchiveV2.Partition;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

@RunWith(Parameterized.class)
public class ParameterArchiveTest {
    String instance = "ParameterArchiveTest";

    @org.junit.runners.Parameterized.Parameter
    public String partitioningSchema;

    @Parameters
    public static List<String> getPartitioningSchemas() {
        return Arrays.asList("none", "YYYY", "YYYY/MM");
    }

    static MockupTimeService timeService;
    static Parameter p1, p2, p3, p4, p5;
    ParameterArchiveV2 parchive;
    ParameterIdDb pidMap;
    ParameterGroupIdDb pgidMap;

    @BeforeClass
    public static void beforeClass() {
        p1 = new Parameter("p1");
        p2 = new Parameter("p2");
        p3 = new Parameter("p3");
        p4 = new Parameter("p4");
        p5 = new Parameter("p5");
        p1.setQualifiedName("/test/p1");
        p2.setQualifiedName("/test/p2");
        p3.setQualifiedName("/test/p3");
        p4.setQualifiedName("/test/p4");
        p5.setQualifiedName("/test/p5");
        TimeEncoding.setUp();

        timeService = new MockupTimeService();
        YamcsServer.setMockupTimeService(timeService);
        // org.yamcs.LoggingUtils.enableLogging();
    }

    @Before
    public void openDb() throws Exception {
        String dbroot = YarchDatabase.getInstance(instance).getRoot();
        FileUtils.deleteRecursively(dbroot);
        FileUtils.deleteRecursively(dbroot + ".rdb");
        FileUtils.deleteRecursively(dbroot + ".tbs");
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        if (rse.getTablespace(instance) != null) {
            rse.dropTablespace(instance);
        }
        rse.createTablespace(instance);
        Map<String, Object> conf = new HashMap<>();

        if (partitioningSchema != null) {
            conf.put("partitioningSchema", partitioningSchema);
        }
        parchive = new ParameterArchiveV2(instance, conf);
        pidMap = parchive.getParameterIdDb();
        pgidMap = parchive.getParameterGroupIdDb();
        assertNotNull(pidMap);
        assertNotNull(pgidMap);
    }

    @After
    public void closeDb() throws Exception {
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        rse.dropTablespace(instance);
    }

    @Test
    public void test1() throws Exception {
        // create a parameter in the map
        int p1id = pidMap.createAndGet("/test/p1", Type.BINARY);

        // close and reopen the archive to check that the parameter is still there

        parchive = new ParameterArchiveV2(instance);
        pidMap = parchive.getParameterIdDb();
        pgidMap = parchive.getParameterGroupIdDb();
        assertNotNull(pidMap);
        assertNotNull(pgidMap);
        int p2id = pidMap.createAndGet("/test/p2", Type.SINT32);

        assertFalse(p1id == p2id);
        assertEquals(p1id, pidMap.createAndGet("/test/p1", Type.BINARY));
    }

    @Test
    public void testSingleParameter() throws Exception {

        ParameterValue pv1_0 = getParameterValue(p1, 100, "blala100", 100);

        int p1id = pidMap.createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType(),
                pv1_0.getRawValue().getType());

        int pg1id = pgidMap.createAndGet(new int[] { p1id });
        PGSegment pgSegment1 = new PGSegment(pg1id, 0, new SortedIntArray(new int[] { p1id }));

        pgSegment1.addRecord(100, Arrays.asList(pv1_0));
        ParameterValue pv1_1 = getParameterValue(p1, 200, "blala200", 200);
        pgSegment1.addRecord(200, Arrays.asList(pv1_1));

        // ascending request on empty data
        List<ParameterValueArray> l0a = retrieveSingleParamSingleGroup(0, 1000, p1id, pg1id, true);
        assertEquals(0, l0a.size());

        // descending request on empty data
        List<ParameterValueArray> l0d = retrieveSingleParamSingleGroup(0, 1000, p1id, pg1id, true);
        assertEquals(0, l0d.size());

        pgSegment1.consolidate();
        parchive.writeToArchive(pgSegment1);

        // ascending request on two value
        List<ParameterValueArray> l4a = retrieveSingleParamSingleGroup(0, TimeEncoding.MAX_INSTANT, p1id, pg1id, true);
        checkEquals(l4a.get(0), pv1_0, pv1_1);

        // descending request on two value
        List<ParameterValueArray> l4d = retrieveSingleParamSingleGroup(0, TimeEncoding.MAX_INSTANT, p1id, pg1id, false);
        checkEquals(l4d.get(0), pv1_1, pv1_0);

        // ascending request on two value with start on first value
        List<ParameterValueArray> l5a = retrieveSingleParamSingleGroup(100, 1000, p1id, pg1id, true);
        checkEquals(l5a.get(0), pv1_0, pv1_1);

        // descending request on two value with start on second value
        List<ParameterValueArray> l5d = retrieveSingleParamSingleGroup(0, 200, p1id, pg1id, false);
        checkEquals(l5d.get(0), pv1_1, pv1_0);

        // ascending request on two value with start on the first value and stop on second
        List<ParameterValueArray> l6a = retrieveSingleParamSingleGroup(100, 200, p1id, pg1id, true);
        checkEquals(l6a.get(0), pv1_0);

        // descending request on two value with start on the second value and stop on first
        List<ParameterValueArray> l6d = retrieveSingleParamSingleGroup(100, 200, p1id, pg1id, false);
        checkEquals(l6d.get(0), pv1_1);

        // new value in a different segment but same partition
        long t2 = SortedTimeSegment.getSegmentEnd(0) + 100;
        PGSegment pgSegment2 = new PGSegment(pg1id, SortedTimeSegment.getSegmentStart(t2),
                new SortedIntArray(new int[] { p1id }));
        ParameterValue pv1_2 = getParameterValue(p1, t2, "pv1_2", 30);
        pv1_2.setAcquisitionStatus(AcquisitionStatus.EXPIRED);

        pgSegment2.addRecord(t2, Arrays.asList(pv1_2));
        pgSegment2.consolidate();
        parchive.writeToArchive(pgSegment2);

        // new value in a different partition
        long t3 = TimeEncoding.parse("2017-01-01T00:00:51");
        PGSegment pgSegment3 = new PGSegment(pg1id, SortedTimeSegment.getSegmentStart(t3),
                new SortedIntArray(new int[] { p1id }));
        ParameterValue pv1_3 = getParameterValue(p1, t3, "pv1_3", 45);
        pgSegment3.addRecord(t3, Arrays.asList(pv1_3));
        pgSegment3.consolidate();
        parchive.writeToArchive(pgSegment3);

        // ascending request on four values
        List<ParameterValueArray> l7a = retrieveSingleParamSingleGroup(0, t3 + 1, p1id, pg1id, true);
        checkEquals(l7a.get(0), pv1_0, pv1_1);
        checkEquals(l7a.get(1), pv1_2);
        checkEquals(l7a.get(2), pv1_3);

        // descending request on four values
        List<ParameterValueArray> l7d = retrieveSingleParamSingleGroup(0, t3 + 1, p1id, pg1id, false);
        checkEquals(l7d.get(0), pv1_3);
        checkEquals(l7d.get(1), pv1_2);
        checkEquals(l7d.get(2), pv1_1, pv1_0);

        // ascending request on the last partition
        List<ParameterValueArray> l8a = retrieveSingleParamSingleGroup(t3, t3 + 1, p1id, pg1id, true);
        checkEquals(l8a.get(0), pv1_3);

        // descending request on the last partition
        List<ParameterValueArray> l8d = retrieveSingleParamSingleGroup(t2, t3, p1id, pg1id, false);
        checkEquals(l8d.get(0), pv1_3);

        // retrieve only statuses
        List<ParameterValueArray> l9a = retrieveSingleParamSingleGroup(0, t3 + 1, p1id, pg1id, false, false, false,
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
    @Test
    public void testRawEqualsEngParameter() throws Exception {
        ParameterValue pv1_0 = getParameterValue(p1, 100, "blala100", "blala100");
        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType(),
                pv1_0.getRawValue().getType());

        int pg1id = parchive.getParameterGroupIdDb().createAndGet(new int[] { p1id });
        PGSegment pgSegment1 = new PGSegment(pg1id, 0, new SortedIntArray(new int[] { p1id }));

        pgSegment1.addRecord(100, Arrays.asList(pv1_0));
        ParameterValue pv1_1 = getParameterValue(p1, 200, "blala200", "blala200");
        pgSegment1.addRecord(200, Arrays.asList(pv1_1));

        pgSegment1.consolidate();

        parchive.writeToArchive(pgSegment1);
        long segmentStart = SortedTimeSegment.getSegmentStart(100);

        Partition p = parchive.getPartitions(100);
        assertNotNull(parchive.getTablespace().getRdb(p.partitionDir)
                .get(new SegmentKey(p1id, pg1id, segmentStart, SegmentKey.TYPE_ENG_VALUE).encode()));
        assertNull(parchive.getTablespace().getRdb()
                .get(new SegmentKey(p1id, pg1id, segmentStart, SegmentKey.TYPE_RAW_VALUE).encode()));

        List<ParameterValueArray> l1a = retrieveSingleParamSingleGroup(0, TimeEncoding.MAX_INSTANT, p1id, pg1id, true,
                false, true, false);
        checkEquals(false, true, false, l1a.get(0), pv1_0, pv1_1);

        List<ParameterValueArray> l1d = retrieveSingleParamSingleGroup(0, TimeEncoding.MAX_INSTANT, p1id, pg1id, false,
                false, true, true);
        checkEquals(false, true, true, l1d.get(0), pv1_1, pv1_0);

        List<ParameterIdValueList> params = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg1id }, true);
        assertEquals(2, params.size());
        ParameterIdValueList pidvl = params.get(0);
        checkEquals(params.get(0), 100, pv1_0);

    }

    List<ParameterValueArray> retrieveSingleParamSingleGroup(long start, long stop, int parameterId,
            int parameterGroupId, boolean ascending, boolean retrieveEngValues, boolean retrieveRawValues,
            boolean retriveParamStatus) throws Exception {
        // ascending request on empty data
        SingleValueConsumer c = new SingleValueConsumer();
        ParameterRequest spvr = new ParameterRequest(start, stop, ascending, retrieveEngValues, retrieveRawValues,
                retriveParamStatus);
        SingleParameterArchiveRetrieval spdr = new SingleParameterArchiveRetrieval(parchive, parameterId,
                new int[] { parameterGroupId }, spvr);
        spdr.retrieve(c);
        return c.list;
    }

    List<ParameterValueArray> retrieveSingleParamSingleGroup(long start, long stop, int parameterId,
            int parameterGroupId, boolean ascending) throws Exception {
        return retrieveSingleParamSingleGroup(start, stop, parameterId, parameterGroupId, ascending, true, true, true);
    }

    ParameterValue getParameterValue(Parameter p, long instant, String sv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngineeringValue(v);
        return pv;
    }

    ParameterValue getParameterValue(Parameter p, long instant, String sv, int rv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngineeringValue(v);
        pv.setRawValue(ValueUtility.getUint32Value(rv));
        return pv;
    }

    ParameterValue getParameterValue(Parameter p, long instant, String sv, String rv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngineeringValue(v);
        pv.setRawValue(ValueUtility.getStringValue(rv));
        return pv;
    }

    @Test
    public void testSingleParameterMultipleGroups() throws Exception {
        ParameterValue pv1_0 = getParameterValue(p1, 100, "pv1_0");
        ParameterValue pv2_0 = getParameterValue(p2, 100, "pv2_0");
        ParameterValue pv1_1 = getParameterValue(p1, 200, "pv1_1");
        ParameterValue pv1_2 = getParameterValue(p1, 300, "pv1_2");
        pv1_2.setAcquisitionStatus(AcquisitionStatus.INVALID);

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType());
        int p2id = parchive.getParameterIdDb().createAndGet(p2.getQualifiedName(), pv2_0.getEngValue().getType());

        int pg1id = parchive.getParameterGroupIdDb().createAndGet(new int[] { p1id, p2id });
        int pg2id = parchive.getParameterGroupIdDb().createAndGet(new int[] { p1id });

        // ascending on empty db
        List<ParameterValueArray> l0a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, true);
        assertEquals(0, l0a.size());
        // descending on empty db
        List<ParameterValueArray> l0d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, false);
        assertEquals(0, l0d.size());

        PGSegment pgSegment1 = new PGSegment(pg1id, 0, new SortedIntArray(new int[] { p1id, p2id }));
        pgSegment1.addRecord(100, Arrays.asList(pv1_0, pv2_0));
        pgSegment1.consolidate();

        PGSegment pgSegment2 = new PGSegment(pg2id, 0, new SortedIntArray(new int[] { p1id }));
        pgSegment2.addRecord(200, Arrays.asList(pv1_1));
        pgSegment2.addRecord(300, Arrays.asList(pv1_2));
        pgSegment2.consolidate();

        parchive.writeToArchive(0, Arrays.asList(pgSegment1, pgSegment2));

        // ascending on 3 values from same segment
        List<ParameterValueArray> l1a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, true);
        assertEquals(1, l1a.size());
        checkEquals(l1a.get(0), pv1_0, pv1_1, pv1_2);

        // descending on 3 values from same segment
        List<ParameterValueArray> l1d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, false);
        assertEquals(1, l1d.size());
        checkEquals(l1d.get(0), pv1_2, pv1_1, pv1_0);

        // new value in a different segment but same partition
        long t2 = SortedTimeSegment.getSegmentEnd(0) + 100;
        PGSegment pgSegment3 = new PGSegment(pg1id, SortedTimeSegment.getSegmentStart(t2),
                new SortedIntArray(new int[] { p1id, p2id }));
        ParameterValue pv1_3 = getParameterValue(p1, t2, "pv1_3");
        ParameterValue pv2_1 = getParameterValue(p1, t2, "pv2_1");
        pgSegment3.addRecord(t2, Arrays.asList(pv1_3, pv2_1));
        pgSegment3.consolidate();
        parchive.writeToArchive(pgSegment3);

        // new value in a different partition
        long t3 = TimeEncoding.parse("2017-01-01T00:00:00");
        PGSegment pgSegment4 = new PGSegment(pg1id, SortedTimeSegment.getSegmentStart(t3),
                new SortedIntArray(new int[] { p1id, p2id }));
        ParameterValue pv1_4 = getParameterValue(p1, t3, "pv1_4");
        ParameterValue pv2_2 = getParameterValue(p1, t3, "pv2_2");
        pgSegment4.addRecord(t3, Arrays.asList(pv1_4, pv2_2));
        pgSegment4.consolidate();
        parchive.writeToArchive(pgSegment4);

        // ascending on 5 values from three segments ascending
        List<ParameterValueArray> l2a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, true);
        assertEquals(3, l2a.size());
        checkEquals(l2a.get(0), pv1_0, pv1_1, pv1_2);
        checkEquals(l2a.get(1), pv1_3);
        checkEquals(l2a.get(2), pv1_4);

        // descending on 3 values from three segments descending
        List<ParameterValueArray> l2d = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, false);
        assertEquals(3, l2d.size());
        checkEquals(l2d.get(0), pv1_4);
        checkEquals(l2d.get(1), pv1_3);
        checkEquals(l2d.get(2), pv1_2, pv1_1, pv1_0);

        // partial retrieval from first segment ascending
        List<ParameterValueArray> l3a = retrieveSingleValueMultigroup(101, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, true);
        assertEquals(3, l3a.size());
        checkEquals(l3a.get(0), pv1_1, pv1_2);
        checkEquals(l3a.get(1), pv1_3);
        checkEquals(l3a.get(2), pv1_4);

        // partial retrieval from first segment descending
        List<ParameterValueArray> l3d = retrieveSingleValueMultigroup(101, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, false);
        assertEquals(3, l3d.size());
        checkEquals(l3d.get(0), pv1_4);
        checkEquals(l3d.get(1), pv1_3);
        checkEquals(l3d.get(2), pv1_2, pv1_1);

        // ascending with empty request inside existing segment
        List<ParameterValueArray> l4a = retrieveSingleValueMultigroup(101, 102, p1id, new int[] { pg1id, pg2id }, true);
        assertEquals(0, l4a.size());

        // descending with empty request inside existing segment
        List<ParameterValueArray> l4d = retrieveSingleValueMultigroup(101, 102, p1id, new int[] { pg1id, pg2id },
                false);
        assertEquals(0, l4d.size());

        // retrieve only statuses
        List<ParameterValueArray> l5a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1id, pg2id }, true, false, false, true);
        assertNull(l5a.get(0).engValues);
        assertNull(l5a.get(1).rawValues);
        assertNull(l5a.get(2).engValues);
        assertNotNull(l5a.get(0).paramStatus);
        assertNotNull(l5a.get(1).paramStatus);
        assertNotNull(l5a.get(2).paramStatus);

        assertEquals(AcquisitionStatus.INVALID, l5a.get(0).paramStatus[2].getAcquisitionStatus());
        assertEquals(AcquisitionStatus.ACQUIRED, l5a.get(2).paramStatus[0].getAcquisitionStatus());

    }

    private List<ParameterValueArray> retrieveSingleValueMultigroup(long start, long stop, int parameterId,
            int[] parameterGroupIds, boolean ascending, boolean retrieveEng, boolean retrieveRaw,
            boolean retrieveStatus) throws RocksDBException, DecodingException, IOException {
        ParameterRequest spvr = new ParameterRequest(start, stop, ascending, retrieveEng, retrieveRaw, retrieveStatus);

        SingleParameterArchiveRetrieval spdr = new SingleParameterArchiveRetrieval(parchive, parameterId,
                parameterGroupIds, spvr);
        SingleValueConsumer svc = new SingleValueConsumer();
        spdr.retrieve(svc);
        return svc.list;
    }

    private List<ParameterValueArray> retrieveSingleValueMultigroup(long start, long stop, int parameterId,
            int[] parameterGroupIds, boolean ascending) throws RocksDBException, DecodingException, IOException {
        return retrieveSingleValueMultigroup(start, stop, parameterId, parameterGroupIds, ascending, true, true, true);
    }

    @Test
    public void testMultipleParameters() throws Exception {
        ParameterValue pv1_0 = getParameterValue(p1, 100, "pv1_0");
        ParameterValue pv2_0 = getParameterValue(p2, 100, "pv2_0");
        ParameterValue pv1_1 = getParameterValue(p1, 200, "pv1_1");
        ParameterValue pv1_2 = getParameterValue(p1, 300, "pv1_2");

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType());
        int p2id = parchive.getParameterIdDb().createAndGet(p2.getQualifiedName(), pv2_0.getEngValue().getType());

        int pg1id = parchive.getParameterGroupIdDb().createAndGet(new int[] { p1id, p2id });
        int pg2id = parchive.getParameterGroupIdDb().createAndGet(new int[] { p1id });

        // ascending on empty db
        List<ParameterIdValueList> l0a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1id, pg1id }, true);
        assertEquals(0, l0a.size());

        // descending on empty db
        List<ParameterIdValueList> l0d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1id, pg1id }, false);
        assertEquals(0, l0d.size());

        PGSegment pgSegment1 = new PGSegment(pg1id, 0, new SortedIntArray(new int[] { p1id, p2id }));
        pgSegment1.addRecord(100, Arrays.asList(pv1_0, pv2_0));
        pgSegment1.consolidate();

        PGSegment pgSegment2 = new PGSegment(pg2id, 0, new SortedIntArray(new int[] { p1id }));
        pgSegment2.addRecord(200, Arrays.asList(pv1_1));
        pgSegment2.addRecord(300, Arrays.asList(pv1_2));
        pgSegment2.consolidate();

        parchive.writeToArchive(0, Arrays.asList(pgSegment1, pgSegment2));

        // ascending, retrieving one parameter from he group of two
        List<ParameterIdValueList> l1a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg1id }, true);
        assertEquals(1, l1a.size());
        checkEquals(l1a.get(0), 100, pv1_0);

        // descending, retrieving one parameter from the group of two
        List<ParameterIdValueList> l1d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg1id }, false);
        assertEquals(1, l1d.size());
        checkEquals(l1d.get(0), 100, pv1_0);

        // ascending, retrieving one para from the group of one
        List<ParameterIdValueList> l2a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg2id }, true);
        assertEquals(2, l2a.size());
        checkEquals(l2a.get(0), 200, pv1_1);
        checkEquals(l2a.get(1), 300, pv1_2);

        // descending, retrieving one para from the group of one
        List<ParameterIdValueList> l2d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT, new int[] { p1id },
                new int[] { pg2id }, false);
        assertEquals(2, l2d.size());
        checkEquals(l2d.get(0), 300, pv1_2);
        checkEquals(l2d.get(1), 200, pv1_1);

        // ascending retrieving two para
        List<ParameterIdValueList> l3a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1id, pg1id }, true);
        assertEquals(1, l3a.size());
        checkEquals(l3a.get(0), 100, pv1_0, pv2_0);

        // descending retrieving two para
        List<ParameterIdValueList> l3d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p2id, p1id }, new int[] { pg1id, pg1id }, false);
        assertEquals(1, l3d.size());
        checkEquals(l3d.get(0), 100, pv1_0, pv2_0);

        // new value in a different segment but same partition
        long t2 = SortedTimeSegment.getSegmentEnd(0) + 100;
        PGSegment pgSegment3 = new PGSegment(pg1id, SortedTimeSegment.getSegmentStart(t2),
                new SortedIntArray(new int[] { p1id, p2id }));
        ParameterValue pv1_3 = getParameterValue(p1, t2, "pv1_3");
        ParameterValue pv2_1 = getParameterValue(p1, t2, "pv2_1");
        pgSegment3.addRecord(t2, Arrays.asList(pv1_3, pv2_1));
        pgSegment3.consolidate();
        parchive.writeToArchive(pgSegment3);

        // new value in a different partition
        long t3 = TimeEncoding.parse("2017-01-01T00:00:00");
        PGSegment pgSegment4 = new PGSegment(pg1id, SortedTimeSegment.getSegmentStart(t3),
                new SortedIntArray(new int[] { p1id, p2id }));
        ParameterValue pv1_4 = getParameterValue(p1, t3, "pv1_4");
        ParameterValue pv2_2 = getParameterValue(p1, t3, "pv2_2");
        pgSegment4.addRecord(t3, Arrays.asList(pv1_4, pv2_2));
        pgSegment4.consolidate();
        parchive.writeToArchive(pgSegment4);

        // ascending retrieving two para
        List<ParameterIdValueList> l4a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1id, pg1id }, true);
        assertEquals(3, l4a.size());
        checkEquals(l4a.get(0), 100, pv1_0, pv2_0);
        checkEquals(l4a.get(1), t2, pv1_3, pv2_1);
        checkEquals(l4a.get(2), t3, pv1_4, pv2_2);

        // descending retrieving two para
        List<ParameterIdValueList> l4d = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p2id, p1id }, new int[] { pg1id, pg1id }, false);
        assertEquals(3, l4d.size());
        checkEquals(l4d.get(0), t3, pv1_4, pv2_2);
        checkEquals(l4d.get(1), t2, pv1_3, pv2_1);
        checkEquals(l4d.get(2), 100, pv1_0, pv2_0);

        // ascending with empty request inside existing segment
        List<ParameterIdValueList> l5a = retrieveMultipleParameters(101, 102, new int[] { p1id, p2id },
                new int[] { pg1id, pg1id }, true);
        assertEquals(0, l5a.size());

        // descending with empty request inside existing segment
        List<ParameterIdValueList> l5d = retrieveMultipleParameters(101, 102, new int[] { p2id, p1id },
                new int[] { pg1id, pg1id }, false);
        assertEquals(0, l5d.size());

        // ascending retrieving two para limited time interval
        List<ParameterIdValueList> l6a = retrieveMultipleParameters(t2, t2 + 1, new int[] { p1id, p2id },
                new int[] { pg1id, pg1id }, true);
        assertEquals(1, l6a.size());
        checkEquals(l6a.get(0), t2, pv1_3, pv2_1);

        // descending retrieving two para limited time interval
        List<ParameterIdValueList> l6d = retrieveMultipleParameters(t2 - 1, t2, new int[] { p2id, p1id },
                new int[] { pg1id, pg1id }, false);
        assertEquals(1, l6d.size());
        checkEquals(l6d.get(0), t2, pv1_3, pv2_1);

        // ascending retrieving two para with limit 2
        List<ParameterIdValueList> l7a = retrieveMultipleParameters(0, TimeEncoding.MAX_INSTANT,
                new int[] { p1id, p2id }, new int[] { pg1id, pg1id }, true, 2);
        assertEquals(2, l7a.size());
        checkEquals(l7a.get(0), 100, pv1_0, pv2_0);
        checkEquals(l7a.get(1), t2, pv1_3, pv2_1);

    }

    @Test
    public void testExpireMillis() throws Exception {
        long t = TimeEncoding.parse("2018-03-19T10:35:00");
        ParameterValue pv1_0 = getParameterValue(p1, t, "blala" + t, (int) t);
        pv1_0.setExpireMillis(1234);

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType(),
                pv1_0.getRawValue().getType());

        int pg1id = parchive.getParameterGroupIdDb().createAndGet(new int[] { p1id });
        PGSegment pgSegment1 = new PGSegment(pg1id, SortedTimeSegment.getSegmentId(t),
                new SortedIntArray(new int[] { p1id }));

        pgSegment1.addRecord(t, Arrays.asList(pv1_0));

        pgSegment1.consolidate();
        parchive.writeToArchive(pgSegment1);

        // ascending request on empty data
        List<ParameterValueArray> l0a = retrieveSingleParamSingleGroup(t, t + 1, p1id, pg1id, true);
        Pvalue.ParameterStatus pstatus = l0a.get(0).paramStatus[0];
        assertTrue(pstatus.hasExpireMillis());
        assertEquals(1234, pstatus.getExpireMillis());

    }

    List<ParameterIdValueList> retrieveMultipleParameters(long start, long stop, int[] parameterIds,
            int[] parameterGroupIds, boolean ascending) throws Exception {
        return retrieveMultipleParameters(start, stop, parameterIds, parameterGroupIds, ascending, -1);
    }

    List<ParameterIdValueList> retrieveMultipleParameters(long start, long stop, int[] parameterIds,
            int[] parameterGroupIds, boolean ascending, int limit) throws Exception {
        String[] parameterNames = new String[parameterIds.length];
        for (int i = 0; i < parameterIds.length; i++) {
            parameterNames[i] = "p" + parameterIds[i];
            ;
        }
        BitSet retrieveRawValues = new BitSet();
        retrieveRawValues.set(0, parameterIds.length);
        MultipleParameterValueRequest mpvr = new MultipleParameterValueRequest(start, stop, parameterNames,
                parameterIds, parameterGroupIds, retrieveRawValues, ascending);
        mpvr.setLimit(limit);

        MultiParameterDataRetrieval mpdr = new MultiParameterDataRetrieval(parchive, mpvr);
        MultiValueConsumer c = new MultiValueConsumer();
        mpdr.retrieve(c);
        return c.list;
    }

    class SingleValueConsumer implements Consumer<ParameterValueArray> {
        List<ParameterValueArray> list = new ArrayList<>();

        @Override
        public void accept(ParameterValueArray x) {
            // System.out.println("received: engValues: "+x.engValues+" rawValues: "+x.rawValues+" paramStatus:
            // "+Arrays.toString(x.paramStatus));
            list.add(x);
        }

    }

    class MultiValueConsumer implements Consumer<ParameterIdValueList> {
        List<ParameterIdValueList> list = new ArrayList<>();

        @Override
        public void accept(ParameterIdValueList x) {
            list.add(x);
        }
    }
}
