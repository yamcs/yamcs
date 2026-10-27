package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.yamcs.parameter.ParameterStatus.isInvalid;
import static org.yamcs.parameter.ParameterStatus.isNominal;
import static org.yamcs.parameterarchive.TestUtils.checkEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameter.ParameterRetrievalService;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.util.AggregateMemberNames;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

public class ParchiveWithSparseGroupsTest extends BaseParchiveTest {

    static Parameter p1, p2, p3, p4, p5, a1;
    static Random random = new Random();

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
        // org.yamcs.LoggingUtils.enableTracing();
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
        pv1_2.setInvalid();

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

        assertTrue(isInvalid(l7a.get(0).paramStatus[2].getAcqStatus()));
        assertTrue(isNominal(l7a.get(2).paramStatus[0].getAcqStatus()));
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

    @Test
    public void testArrayComponentsAddedAfterSegmentArchived() throws Exception {
        openDb("none", true, 0.5);

        int p0x = pidMap.createAndGet("/test/a[0].x", Type.STRING);
        int p0y = pidMap.createAndGet("/test/a[0].y", Type.STRING);
        var initialGroup = pgidMap.getGroup(IntArray.wrap(p0x, p0y));

        ParameterValue pv0x = getParameterValue(p1, 100, "x0");
        ParameterValue pv0y = getParameterValue(p2, 100, "y0");
        pv0x.setInvalid();
        pv0y.setInvalid();
        PGSegment segment = new PGSegment(initialGroup.id, 0);
        segment.addRecord(100, IntArray.wrap(p0x, p0y), Arrays.asList(pv0x, pv0y));
        parchive.writeToArchive(segment);

        // Extend the group only after the older segment has been written. The new array element has no RocksDB record in
        // that segment, including no gap record.
        int p1x = pidMap.createAndGet("/test/a[1].x", Type.STRING);
        int p1y = pidMap.createAndGet("/test/a[1].y", Type.STRING);
        var extendedGroup = pgidMap.getGroup(IntArray.wrap(p0x, p0y, p1x, p1y));
        assertEquals(initialGroup.id, extendedGroup.id);

        int arrayId = pidMap.createAndGetAggrray("/test/a", Type.ARRAY, null,
                IntArray.wrap(p0x, p0y, p1x, p1y));

        assertArchivedArrayValue(arrayId, extendedGroup.id, true);
        assertArchivedArrayValue(arrayId, extendedGroup.id, false);
    }

    @Test
    public void testNestedEmptyArraysAreArchived() throws Exception {
        openDb("none", true, 0.1);
        Parameter configuration = new Parameter("configuration");
        configuration.setQualifiedName("/test/configuration");

        BasicParameterList outerEmpty = getConfigurationList(configuration, 100, 0, 0, 0);
        BasicParameterList middleEmpty = getConfigurationList(configuration, 200, 1, 0, 0);
        BasicParameterList innerEmpty = getConfigurationList(configuration, 300, 1, 1, 0);
        BasicParameterList populated = getConfigurationList(configuration, 400, 1, 1, 1);

        var group = pgidMap.getGroup(outerEmpty.getPids());
        assertEquals(group.id, pgidMap.getGroup(middleEmpty.getPids()).id);
        assertEquals(group.id, pgidMap.getGroup(innerEmpty.getPids()).id);
        assertEquals(group.id, pgidMap.getGroup(populated.getPids()).id);

        PGSegment segment = new PGSegment(group.id, 0);
        segment.addRecord(100, outerEmpty.getPids(), outerEmpty.getValues());
        segment.addRecord(200, middleEmpty.getPids(), middleEmpty.getValues());
        segment.addRecord(300, innerEmpty.getPids(), innerEmpty.getValues());
        segment.addRecord(400, populated.getPids(), populated.getValues());
        parchive.writeToArchive(segment);

        ParameterId[] rootIds = pidMap.get(configuration.getQualifiedName());
        assertEquals(1, rootIds.length);
        List<ParameterIdValueList> values = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { rootIds[0].getPid() }, new int[] { group.id }, true);
        assertEquals(4, values.size());
        assertConfigurationDimensions(values.get(0), 0, 0, 0);
        assertConfigurationDimensions(values.get(1), 1, 0, 0);
        assertConfigurationDimensions(values.get(2), 1, 1, 0);
        assertConfigurationDimensions(values.get(3), 1, 1, 1);

        ParameterRetrievalService retrievalService = new ParameterRetrievalService();
        retrievalService.init(instance, "retrieval", YConfiguration.emptyConfig());
        setField(retrievalService, "parchive", parchive);
        try {
            ParameterWithId reportsRequest = new ParameterWithId(configuration,
                    NamedObjectId.newBuilder().setName(configuration.getQualifiedName() + ".reports").build(),
                    AggregateUtil.parseReference("reports"));
            ParameterRetrievalOptions options = ParameterRetrievalOptions.newBuilder()
                    .withStartStop(0, 200)
                    .withAscending(true)
                    .withoutReplay(true)
                    .build();
            List<ParameterValueWithId> memberValues = new ArrayList<>();
            retrievalService.retrieveSingle(reportsRequest, options, memberValues::add).get();

            assertEquals(1, memberValues.size());
            assertEquals(0, ((ArrayValue) memberValues.get(0).getParameterValue().getEngValue()).flatLength());

            List<List<ParameterValueWithId>> multiMemberValues = new ArrayList<>();
            retrievalService.retrieveMulti(List.of(reportsRequest), options, multiMemberValues::add).get();
            assertEquals(1, multiMemberValues.size());
            assertEquals(1, multiMemberValues.get(0).size());
            assertEquals(0,
                    ((ArrayValue) multiMemberValues.get(0).get(0).getParameterValue().getEngValue()).flatLength());
        } finally {
            ((ExecutorService) getField(retrievalService, "executor")).shutdownNow();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private BasicParameterList getConfigurationList(Parameter parameter, long time, int reportCount, int serviceCount,
            int subserviceCount) {
        AggregateValue configuration = new AggregateValue(
                AggregateMemberNames.get(new String[] { "report_count", "reports" }));
        configuration.setMemberValue("report_count", ValueUtility.getUint32Value(reportCount));
        ArrayValue reports = new ArrayValue(new int[] { reportCount }, Type.AGGREGATE);
        if (reportCount > 0) {
            AggregateValue report = new AggregateValue(
                    AggregateMemberNames.get(new String[] { "apid", "service_count", "services" }));
            report.setMemberValue("apid", ValueUtility.getUint32Value(11));
            report.setMemberValue("service_count", ValueUtility.getUint32Value(serviceCount));
            ArrayValue services = new ArrayValue(new int[] { serviceCount }, Type.AGGREGATE);
            if (serviceCount > 0) {
                AggregateValue service = new AggregateValue(AggregateMemberNames.get(
                        new String[] { "service_type", "subservice_count", "subservices" }));
                service.setMemberValue("service_type", ValueUtility.getUint32Value(14));
                service.setMemberValue("subservice_count", ValueUtility.getUint32Value(subserviceCount));
                ArrayValue subservices = new ArrayValue(new int[] { subserviceCount }, Type.UINT32);
                if (subserviceCount > 0) {
                    subservices.setElementValue(0, ValueUtility.getUint32Value(4));
                }
                service.setMemberValue("subservices", subservices);
                services.setElementValue(0, service);
            }
            report.setMemberValue("services", services);
            reports.setElementValue(0, report);
        }
        configuration.setMemberValue("reports", reports);

        ParameterValue pv = new ParameterValue(parameter);
        pv.setGenerationTime(time);
        pv.setEngValue(configuration);
        BasicParameterList list = new BasicParameterList(pidMap);
        list.add(pv);
        return list;
    }

    private void assertConfigurationDimensions(ParameterIdValueList valueList, int reportCount, int serviceCount,
            int subserviceCount) {
        AggregateValue configuration = (AggregateValue) valueList.getValues().get(0).getEngValue();
        ArrayValue reports = (ArrayValue) configuration.getMemberValue("reports");
        assertEquals(reportCount, reports.flatLength());
        if (reportCount > 0) {
            AggregateValue report = (AggregateValue) reports.getElementValue(0);
            ArrayValue services = (ArrayValue) report.getMemberValue("services");
            assertEquals(serviceCount, services.flatLength());
            if (serviceCount > 0) {
                AggregateValue service = (AggregateValue) services.getElementValue(0);
                ArrayValue subservices = (ArrayValue) service.getMemberValue("subservices");
                assertEquals(subserviceCount, subservices.flatLength());
            }
        }
    }

    private void assertArchivedArrayValue(int arrayId, int parameterGroupId, boolean ascending) throws Exception {
        List<ParameterIdValueList> values = retrieveMultipleParameters(0, TimeEncoding.POSITIVE_INFINITY,
                new int[] { arrayId }, new int[] { parameterGroupId }, ascending);
        assertEquals(1, values.size());
        assertEquals(1, values.get(0).size());

        ParameterValue parameterValue = values.get(0).getValues().get(0);
        assertTrue(parameterValue.isInvalid());
        ArrayValue arrayValue = (ArrayValue) parameterValue.getEngValue();
        assertEquals(1, arrayValue.flatLength());
        AggregateValue element = (AggregateValue) arrayValue.getElementValue(0);
        assertEquals("x0", element.getMemberValue("x").getStringValue());
        assertEquals("y0", element.getMemberValue("y").getStringValue());
    }

    @Test
    public void test3() throws Exception {
        openDb("none", true, 1);

        BackFillerTask task = new BackFillerTask(parchive);
        task.maxSegmentSize = 2;

        // segment 0 - only p1
        ParameterValue pv1_0 = getParameterValue(p1, 0, "pv1_0");
        ParameterValue pv1_1 = getParameterValue(p1, 100, "pv1_1");
        task.processParameters(Arrays.asList(pv1_0));
        task.processParameters(Arrays.asList(pv1_1));

        // segment 1 - both p1 and p2
        ParameterValue pv1_2 = getParameterValue(p1, 200, "pv1_2");
        ParameterValue pv2_2 = getParameterValue(p2, 200, "pv2_2");
        ParameterValue pv1_3 = getParameterValue(p1, 300, "pv1_3");
        ParameterValue pv2_3 = getParameterValue(p2, 300, "pv2_3");
        task.processParameters(Arrays.asList(pv1_2, pv2_2));
        task.processParameters(Arrays.asList(pv1_3, pv2_3));

        // segment 2 - p1 and partially p2
        ParameterValue pv1_4 = getParameterValue(p1, 400, "pv1_4");
        ParameterValue pv2_4 = getParameterValue(p2, 400, "pv2_4");
        ParameterValue pv1_5 = getParameterValue(p1, 500, "pv1_5");
        task.processParameters(Arrays.asList(pv1_4, pv2_4));
        task.processParameters(Arrays.asList(pv1_5));

        // segment 3 - only p1
        ParameterValue pv1_6 = getParameterValue(p1, 600, "pv1_6");
        ParameterValue pv1_7 = getParameterValue(p1, 700, "pv1_7");
        task.processParameters(Arrays.asList(pv1_6));
        task.processParameters(Arrays.asList(pv1_7));

        // segment 4 - p1 and partially p2
        ParameterValue pv1_8 = getParameterValue(p1, 800, "pv1_8");
        ParameterValue pv1_9 = getParameterValue(p1, 900, "pv1_9");
        ParameterValue pv2_9 = getParameterValue(p2, 900, "pv2_9");
        task.processParameters(Arrays.asList(pv1_8));
        task.processParameters(Arrays.asList(pv1_9, pv2_9));

        // segment 5 - both p1 and p2
        ParameterValue pv1_10 = getParameterValue(p1, 1000, "pv1_10");
        ParameterValue pv2_10 = getParameterValue(p2, 1000, "pv2_10");
        ParameterValue pv1_11 = getParameterValue(p1, 1100, "pv1_11");
        ParameterValue pv2_11 = getParameterValue(p2, 1100, "pv2_11");
        task.processParameters(Arrays.asList(pv1_10, pv2_10));
        task.processParameters(Arrays.asList(pv1_11, pv2_11));

        // segment 6 - only p1
        ParameterValue pv1_12 = getParameterValue(p1, 1200, "pv1_12");
        ParameterValue pv1_13 = getParameterValue(p1, 1300, "pv1_13");
        task.processParameters(Arrays.asList(pv1_12));
        task.processParameters(Arrays.asList(pv1_13));

        task.flush();
        var p1id = parchive.getParameterIdDb().get(p1.getQualifiedName())[0].getPid();
        var p2id = parchive.getParameterIdDb().get(p2.getQualifiedName())[0].getPid();
        var pgid = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id)).id;

        List<ParameterIdValueList> l = retrieveMultipleParameters(0,
                TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id }, new int[] { pgid, pgid }, true);
        assertEquals(14, l.size());

        checkEquals(l.get(0), 0, pv1_0);
        checkEquals(l.get(1), 100, pv1_1);
        checkEquals(l.get(2), 200, pv1_2, pv2_2);
        checkEquals(l.get(3), 300, pv1_3, pv2_3);
        checkEquals(l.get(4), 400, pv1_4, pv2_4);
        checkEquals(l.get(5), 500, pv1_5);
        checkEquals(l.get(6), 600, pv1_6);
        checkEquals(l.get(7), 700, pv1_7);
        checkEquals(l.get(8), 800, pv1_8);
        checkEquals(l.get(9), 900, pv1_9, pv2_9);
        checkEquals(l.get(10), 1000, pv1_10, pv2_10);
        checkEquals(l.get(11), 1100, pv1_11, pv2_11);
        checkEquals(l.get(12), 1200, pv1_12);
        checkEquals(l.get(13), 1300, pv1_13);

    }

    @Test
    public void test3bis() throws Exception {
        openDb("none", true, 1);

        BackFillerTask task = new BackFillerTask(parchive);
        task.maxSegmentSize = 2;

        // segment 0 - p1 and partially p2
        ParameterValue pv1_1 = getParameterValue(p1, 100, "pv1_1");
        ParameterValue pv1_2 = getParameterValue(p1, 200, "pv1_2");
        ParameterValue pv2_2 = getParameterValue(p2, 200, "pv2_2");
        task.processParameters(Arrays.asList(pv1_1));
        task.processParameters(Arrays.asList(pv1_2, pv2_2));

        // segment 1 - both p1 and p2
        ParameterValue pv1_3 = getParameterValue(p1, 300, "pv1_3");
        ParameterValue pv2_3 = getParameterValue(p2, 300, "pv2_3");
        ParameterValue pv1_4 = getParameterValue(p1, 400, "pv1_4");
        ParameterValue pv2_4 = getParameterValue(p2, 400, "pv2_4");
        task.processParameters(Arrays.asList(pv1_3, pv2_3));
        task.processParameters(Arrays.asList(pv1_4, pv2_4));

        // segment 2 - only p1
        ParameterValue pv1_5 = getParameterValue(p1, 500, "pv1_5");
        ParameterValue pv1_6 = getParameterValue(p1, 600, "pv1_6");
        task.processParameters(Arrays.asList(pv1_5));
        task.processParameters(Arrays.asList(pv1_6));

        // segment 3 - only p1
        ParameterValue pv1_7 = getParameterValue(p1, 700, "pv1_7");
        ParameterValue pv1_8 = getParameterValue(p1, 800, "pv1_8");
        task.processParameters(Arrays.asList(pv1_7));
        task.processParameters(Arrays.asList(pv1_8));

        // segment 4 - both p1 and p2
        ParameterValue pv1_9 = getParameterValue(p1, 900, "pv1_9");
        ParameterValue pv2_9 = getParameterValue(p2, 900, "pv2_9");
        ParameterValue pv1_10 = getParameterValue(p1, 1000, "pv1_10");
        ParameterValue pv2_10 = getParameterValue(p2, 1000, "pv2_10");
        task.processParameters(Arrays.asList(pv1_9, pv2_9));
        task.processParameters(Arrays.asList(pv1_10, pv2_10));

        task.flush();
        var p1id = parchive.getParameterIdDb().get(p1.getQualifiedName())[0].getPid();
        var p2id = parchive.getParameterIdDb().get(p2.getQualifiedName())[0].getPid();
        var pgid = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id)).id;

        List<ParameterIdValueList> l = retrieveMultipleParameters(0,
                TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id }, new int[] { pgid, pgid }, true);
        assertEquals(10, l.size());

        checkEquals(l.get(0), 100, pv1_1);
        checkEquals(l.get(1), 200, pv1_2, pv2_2);
        checkEquals(l.get(2), 300, pv1_3, pv2_3);
        checkEquals(l.get(3), 400, pv1_4, pv2_4);
        checkEquals(l.get(4), 500, pv1_5);
        checkEquals(l.get(5), 600, pv1_6);
        checkEquals(l.get(6), 700, pv1_7);
        checkEquals(l.get(7), 800, pv1_8);
        checkEquals(l.get(8), 900, pv1_9, pv2_9);
        checkEquals(l.get(9), 1000, pv1_10, pv2_10);
    }

    @Test
    public void testUnsorted() throws Exception {
        openDb("none", true, 1);

        BackFillerTask task = new BackFillerTask(parchive);
        task.maxSegmentSize = 3;

        // interval 0 - p1 and p2 - to make sure that p1 and p2 are in the same group
        ParameterValue pv1_1 = getParameterValue(p1, 100, "pv1_1");
        ParameterValue pv2_1 = getParameterValue(p2, 100, "pv2_1");
        task.processParameters(Arrays.asList(pv1_1, pv2_1));


        // interval 1 - p1, p1 and then p2 in between
        long t1 = ParameterArchive.getIntervalEnd(0) + 1;
        ParameterValue pv1_2 = getParameterValue(p1, t1, "pv1_2");
        ParameterValue pv1_3 = getParameterValue(p1, t1 + 200, "pv1_3");
        ParameterValue pv2_2 = getParameterValue(p2, t1 + 100, "pv2_2");

        task.processParameters(Arrays.asList(pv1_2));
        task.processParameters(Arrays.asList(pv1_3));
        task.processParameters(Arrays.asList(pv2_2));
        task.flush();

        var p1id = parchive.getParameterIdDb().get(p1.getQualifiedName())[0].getPid();
        var p2id = parchive.getParameterIdDb().get(p2.getQualifiedName())[0].getPid();
        var pgid = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id)).id;

        List<ParameterIdValueList> l = retrieveMultipleParameters(0,
                TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id }, new int[] { pgid, pgid }, true);
        assertEquals(4, l.size());
        checkEquals(l.get(0), 100, pv1_1, pv2_1);
        checkEquals(l.get(1), t1, pv1_2);
        checkEquals(l.get(2), t1 + 100, pv2_2);
        checkEquals(l.get(3), t1 + 200, pv1_3);
    }

    @Test
    public void testRandomized() throws Exception {
        openDb("none", true, 0);

        BackFillerTask task = new BackFillerTask(parchive);
        task.maxSegmentSize = 2;
        int maxNumParams = 30;
        List<Parameter> params = new ArrayList<>(maxNumParams);
        for (int i = 0; i < maxNumParams; i++) {
            var p = new Parameter("p" + i);
            p.setQualifiedName("/randomized_test/" + p.getName());
            params.add(p);
            // add parameters to the parameter id db in order to make sure they get increasing numbers
            parchive.getParameterIdDb().createAndGet(p.getQualifiedName(), Type.STRING);
        }
        /*     var sl = List.of(a(0), a(0, 0), a(0, 1), a(0, 0), a(0, 1), a(0, 0), a(0, 0), a(0, 0), a(1), a(1, 1), a(0, 1),
                a(1, 0), a(1), a(0), a(1), a(0), a(1), a(0), a(0), a(0));
        testRandomOne(task, params, sl, 0);
          */
        long t0 = 0;
        for (int numParams = 2; numParams < maxNumParams; numParams++) {
            for (int i = 0; i < 100; i++) {
                var setList = generateSetList(numParams, 100);
                try {
                    testRandomOne(task, params, setList, t0);
                } catch (Throwable e) {
                    fail(toString(setList), e);
                }
                t0 = ParameterArchive.getIntervalEnd(t0) + 1;
            }
        }
    }

    public void testRandomOne(BackFillerTask task, List<Parameter> params, List<IntArray> setList, long t0)
            throws Exception {
        List<ParameterValue[]> expectedValueList = new ArrayList<>();

        for (int k = 0; k < setList.size(); k++) {
            var set = setList.get(k);
            List<ParameterValue> values = new ArrayList<>();
            for (int pid : set) {
                Parameter param = params.get(pid);
                ParameterValue pv = getParameterValue(param, t0 + k, "pv" + pid + "_" + k);
                values.add(pv);

            }
            task.processParameters(values);
            var sortedValues = IntStream.range(0, values.size())
                    .boxed()
                    .sorted(Comparator.comparing(set::get))
                    .map(values::get)
                    .toArray(ParameterValue[]::new);
            // the parameter archive returns values in the order of their parameter ids
            expectedValueList.add(sortedValues);
        }
        task.flush();
        var paramIds = params.stream()
                .map(p -> parchive.getParameterIdDb().get(p.getQualifiedName())[0].getPid())
                .mapToInt(Integer::intValue)
                .toArray();
        List<ParameterIdValueList> l = retrieveMultipleParameters(t0,
                TimeEncoding.POSITIVE_INFINITY,
                paramIds, null, true);

        for (int k = 0; k < expectedValueList.size(); k++) {
            checkEquals(l.get(k), t0 + k, expectedValueList.get(k));
        }
    }

    @Test
    public void test4() throws Exception {
        openDb("none", true, 0);

        BackFillerTask task = new BackFillerTask(parchive);
        task.maxSegmentSize = 2;

        // segment 0 - only p1
        ParameterValue pv1_0 = getParameterValue(p1, 0, "pv1_0");
        ParameterValue pv1_1 = getParameterValue(p1, 100, "pv1_1");
        task.processParameters(Arrays.asList(pv1_0));
        task.processParameters(Arrays.asList(pv1_1));

        // segment 1 - only p1
        ParameterValue pv1_2 = getParameterValue(p1, 200, "pv1_2");
        ParameterValue pv1_3 = getParameterValue(p1, 300, "pv1_3");
        task.processParameters(Arrays.asList(pv1_2));
        task.processParameters(Arrays.asList(pv1_3));

        // segment 2 - partially p1 and p2
        ParameterValue pv1_4 = getParameterValue(p1, 400, "pv1_4");
        ParameterValue pv2_4 = getParameterValue(p2, 400, "pv2_4");
        ParameterValue pv2_5 = getParameterValue(p2, 500, "pv2_5");
        task.processParameters(Arrays.asList(pv2_4, pv1_4));
        task.processParameters(Arrays.asList(pv2_5));

        // segment 2 - only p2
        ParameterValue pv2_6 = getParameterValue(p2, 600, "pv2_6");
        ParameterValue pv2_7 = getParameterValue(p2, 700, "pv2_7");
        task.processParameters(Arrays.asList(pv2_6));
        task.processParameters(Arrays.asList(pv2_7));

        task.flush();
        var p1id = parchive.getParameterIdDb().get(p1.getQualifiedName())[0].getPid();
        var p2id = parchive.getParameterIdDb().get(p2.getQualifiedName())[0].getPid();
        var pgid = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id)).id;

        List<ParameterIdValueList> l = retrieveMultipleParameters(0,
                TimeEncoding.POSITIVE_INFINITY,
                new int[] { p1id, p2id }, new int[] { pgid, pgid }, true);
        assertEquals(8, l.size());

        checkEquals(l.get(0), 0, pv1_0);
        checkEquals(l.get(1), 100, pv1_1);
        checkEquals(l.get(2), 200, pv1_2);
        checkEquals(l.get(3), 300, pv1_3);
        checkEquals(l.get(4), 400, pv1_4, pv2_4);
        checkEquals(l.get(5), 500, pv2_5);
        checkEquals(l.get(6), 600, pv2_6);
        checkEquals(l.get(7), 700, pv2_7);

    }

    @Test
    public void testOrphanRemoval() throws Exception {
        openDb("none", true, 0.5);
        ParameterValue pv1_0 = getParameterValue(p1, 100, "pv1_0");
        ParameterValue pv2_0 = getParameterValue(p2, 100, "pv2_0");
        ParameterValue pv1_2 = getParameterValue(p1, 300, "pv1_2");
        pv1_2.setInvalid();

        int p1id = parchive.getParameterIdDb().createAndGet(p1.getQualifiedName(), pv1_0.getEngValue().getType());
        int p2id = parchive.getParameterIdDb().createAndGet(p2.getQualifiedName(), pv2_0.getEngValue().getType());

        var pg1 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id, p2id));
        var pg2 = parchive.getParameterGroupIdDb().getGroup(IntArray.wrap(p1id));

        assertEquals(pg1.id, pg2.id);

        PGSegment pgSegment1 = new PGSegment(pg1.id, 0);
        pgSegment1.addRecord(100, IntArray.wrap(p1id, p2id), Arrays.asList(pv1_0, pv2_0));
        parchive.writeToArchive(0, Arrays.asList(pgSegment1));

        List<ParameterValueArray> l1a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, true);
        assertEquals(1, l1a.size());

        PGSegment pgSegment2 = new PGSegment(pg1.id, 0);
        pgSegment2.addRecord(100, IntArray.wrap(p2id), Arrays.asList(pv2_0));
        parchive.writeToArchive(0, Arrays.asList(pgSegment2));

        List<ParameterValueArray> l2a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p1id,
                new int[] { pg1.id }, true);
        assertEquals(0, l2a.size());

        List<ParameterValueArray> l3a = retrieveSingleValueMultigroup(0, TimeEncoding.MAX_INSTANT, p2id,
                new int[] { pg1.id }, true);
        assertEquals(1, l3a.size());

    }

    public static List<IntArray> generateSetList(int maxSetSize, int listLength) {
        List<IntArray> list = new ArrayList<>();

        for (int j = 0; j < listLength; j++) {
            IntArray set = new IntArray();
            int setSize = 1 + random.nextInt(maxSetSize);
            while (set.size() < setSize) {
                set.add(random.nextInt(maxSetSize));
            }
            list.add(set);
        }
        return list;
    }

    String toString(List<IntArray> setList) {
        StringBuilder sb = new StringBuilder("List.of(");
        boolean first0 = true;
        for (var set : setList) {
            if (first0) {
                first0 = false;
            } else {
                sb.append(", ");
            }
            sb.append("a(");
            boolean first1 = true;
            for (var i : set) {
                if (first1) {
                    first1 = false;
                } else {
                    sb.append(", ");
                }
                sb.append(i);
            }
            sb.append(")");

        }
        sb.append("); ");
        return sb.toString();
    }

    IntArray a(int... a) {
        return IntArray.wrap(a);
    }
}
