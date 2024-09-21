package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.client.Page;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.archive.ArchiveClient.ListOptions;
import org.yamcs.client.archive.ArchiveClient.RangeOptions;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.Ranges.Range;
import org.yamcs.protobuf.Pvalue.TimeSeries.Sample;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.util.Timestamps;

public class ParameterArchiveIntegrationTest extends AbstractIntegrationTest {

    private ArchiveClient archiveClient;

    @BeforeEach
    public void cleanParameterCache() {
        Processor p = YamcsServer.getServer().getProcessor(yamcsInstance, "realtime");
        p.getParameterCache().clear();
        archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
    }

    @Test
    public void testRetrieval() throws Exception {
        generatePkt13AndPps("2015-01-02T10:00:00", 2 * 3600);

        Value engValue;
        org.yamcs.protobuf.Pvalue.ParameterValue pv;
        Sample s0;

        // first two requests before the consolidation, should return data from cache
        Instant start = Instant.parse("2015-01-02T10:00:00Z");
        Instant stop = Instant.parse("2015-01-02T11:00:00Z");
        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(100, values.size());
        pv = values.get(0);
        engValue = pv.getEngValue();
        assertEquals(0.167291805148, engValue.getFloatValue(), 1e-5);
        assertEquals(2850, pv.getExpireMillis());

        start = Instant.parse("2015-01-02T11:40:00Z");
        stop = Instant.parse("2015-01-02T12:00:00Z");
        List<Sample> samples = archiveClient.getSamples("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();

        assertEquals(500, samples.size());
        s0 = samples.get(0);
        assertEquals(0.167291805148, s0.getMin(), 1e-5);
        assertEquals(0.167291805148, s0.getMax(), 1e-5);
        assertEquals(0.167291805148, s0.getAvg(), 1e-5);

        buildParameterArchive("2015-01-02T10:00:00", "2016-01-02T11:00:00");

        start = Instant.parse("2015-01-02T10:00:00Z");
        stop = Instant.parse("2015-01-02T11:00:00Z");

        samples = archiveClient.getSamples("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();
        assertEquals(500, samples.size());
        s0 = samples.get(0);
        assertEquals(0.167291805148, s0.getMin(), 1e-5);
        assertEquals(0.167291805148, s0.getMax(), 1e-5);
        assertEquals(0.167291805148, s0.getAvg(), 1e-5);

        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(100, values.size());
        pv = values.get(0);
        engValue = pv.getEngValue();
        assertEquals(0.167291805148, engValue.getFloatValue(), 1e-5);
        assertEquals(2850, pv.getExpireMillis());

        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                ListOptions.limit(10)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(10, values.size());

        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                ListOptions.noRepeat(true)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(1, values.size());
        pv = values.get(0);

        assertEquals("2015-01-02T11:00:00Z", Timestamps.toString(pv.getGenerationTime()));
        assertEquals(0.167291805148, pv.getEngValue().getFloatValue(), 1e-5);
        AcquisitionStatus acqs = values.get(0).getAcquisitionStatus();
        assertEquals(AcquisitionStatus.ACQUIRED, acqs);

        // add some realtime data
        generatePkt13AndPps("2015-01-02T12:00:00", 10);

        stop = Instant.parse("2015-01-03T11:59:00Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", null, stop,
                ListOptions.limit(20)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(20, values.size());
        long t = TimeEncoding.parse("2015-01-02T12:00:09.000");
        for (ParameterValue value : values) {
            assertEquals(t, TimeEncoding.fromProtobufTimestamp(value.getGenerationTime()));
            t -= 1000;
        }

        start = Instant.parse("2015-01-02T12:00:00Z");
        stop = Instant.parse("2015-01-03T11:59:00Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(9, values.size());
        t = TimeEncoding.parse("2015-01-02T12:00:09.000");
        for (ParameterValue value : values) {
            assertEquals(t, TimeEncoding.fromProtobufTimestamp(value.getGenerationTime()));
            t -= 1000;
        }

        // request excluding realtime cache
        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                ListOptions.noRealtime(true)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(0, values.size());

        // ascending request combining archive with cache
        start = Instant.parse("2015-01-02T11:59:50Z");
        stop = Instant.parse("2015-01-03T11:59:00Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                ListOptions.ascending(true)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(20, values.size());
        t = TimeEncoding.parse("2015-01-02T11:59:50");
        for (ParameterValue value : values) {
            assertEquals(t, TimeEncoding.fromProtobufTimestamp(value.getGenerationTime()));
            t += 1000;
        }
    }

    @Test
    public void testWithEnums() throws Exception {
        generatePkt13AndPps("2020-12-08T10:00:00", 3600);
        // org.yamcs.LoggingUtils.enableLogging(Level.ALL);
        buildParameterArchive("2020-12-08T10:00:00", "2020-12-08T11:00:00");
        Instant start = Instant.parse("2020-12-08T10:00:00Z");
        Instant stop = Instant.parse("2020-12-08T10:00:19.59Z");
        Page<ParameterValue> page = archiveClient
                .listValues("/REFMDB/SUBSYS1/EnumerationPara1_1_4", start, stop, ListOptions.ascending(true)).get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(20, values.size());
        ParameterValue pv = values.get(0);
        Value engValue = pv.getEngValue();
        assertEquals("zero_yep", engValue.getStringValue());

        List<Range> ranges = archiveClient.getRanges("/REFMDB/SUBSYS1/EnumerationPara1_1_4", start, stop).get();
        assertEquals(1, ranges.size());
        Range r0 = ranges.get(0);
        assertEquals(20, r0.getCounts(0));
        assertEquals("zero_yep", r0.getEngValues(0).getStringValue());
    }

    @Test
    public void testRanges() throws Exception {
        generatePkt13AndPps("2018-01-01T10:00:00", 2 * 3600);

        // first request before the consolidation, should return data from cache
        Instant start = Instant.parse("2018-01-01T11:40:00Z");
        Instant stop = Instant.parse("2018-01-02T12:00:00Z");

        List<Range> ranges = archiveClient.getRanges("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();

        assertEquals(1, ranges.size());
        Range r0 = ranges.get(0);
        assertEquals(1199, r0.getCount());
        assertEquals(1199, r0.getCounts(0));
        assertEquals(0.167291805148, r0.getEngValues(0).getFloatValue(), 1e-5);
        assertEquals("2018-01-01T11:40:01.000Z",
                TimeEncoding.toString(TimeEncoding.fromProtobufTimestamp(r0.getStart())));
        assertEquals("2018-01-01T11:59:59.000Z",
                TimeEncoding.toString(TimeEncoding.fromProtobufTimestamp(r0.getStop())));

        buildParameterArchive("2018-01-01T10:00:00", "2018-01-02T11:00:00");

        start = Instant.parse("2018-01-01T10:00:00Z");
        stop = Instant.parse("2018-01-02T11:00:00Z");
        ranges = archiveClient.getRanges("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();
        assertEquals(1, ranges.size());
        r0 = ranges.get(0);
        assertEquals(7200, r0.getCounts(0));
        assertEquals(0.167291805148, r0.getEngValues(0).getFloatValue(), 1e-5);

        generatePkt13AndPps("2018-01-01T13:00:00", 3600);

        ranges = archiveClient.getRanges("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop).get();

        assertEquals(2, ranges.size());
        r0 = ranges.get(0);
        assertEquals(7200, r0.getCounts(0));

        assertEquals("2018-01-01T10:00:00.000Z",
                TimeEncoding.toString(TimeEncoding.fromProtobufTimestamp(r0.getStart())));
        // last parameter time plus expiration
        assertEquals("2018-01-01T12:00:01.850Z",
                TimeEncoding.toString(TimeEncoding.fromProtobufTimestamp(r0.getStop())));

        Range r1 = ranges.get(1);
        assertEquals(3600, r1.getCounts(0));
        assertEquals("2018-01-01T13:00:00.000Z",
                TimeEncoding.toString(TimeEncoding.fromProtobufTimestamp(r1.getStart())));
        assertEquals("2018-01-01T13:59:59.000Z",
                TimeEncoding.toString(TimeEncoding.fromProtobufTimestamp(r1.getStop())));

        start = Instant.parse("2018-01-01T10:00:00Z");
        stop = Instant.parse("2018-01-02T11:00:00Z");
        ranges = archiveClient.getRanges("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                RangeOptions.minimumGap(3601001)).get();

        assertEquals(1, ranges.size());
        r0 = ranges.get(0);
        assertEquals(7200 + 3600, r0.getCounts(0));

        ranges = archiveClient.getRanges("/REFMDB/SUBSYS1/FloatPara1_1_2", start, stop,
                RangeOptions.minimumRange(4 * 3600000l)).get();
        assertEquals(1, ranges.size());
        r0 = ranges.get(0);
        assertEquals(7200 + 3600, r0.getCounts(0));

    }

    @Test
    public void testWithAggregateMembers() throws Exception {
        generatePkt7("2019-04-06T00:00:00", 2 * 3600);

        // first two requests before the consolidation, should return data from cache
        Instant start = Instant.parse("2019-04-06T01:59:00Z");
        Instant stop = Instant.parse("2019-04-06T03:00:00Z");
        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/aggregate_para1.member2", start, stop)
                .get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(59, values.size());
        org.yamcs.protobuf.Pvalue.ParameterValue pv = values.get(0);
        Value engValue = pv.getEngValue();
        assertEquals(packetGenerator.paggr1_member2, engValue.getUint32Value());
        assertFalse(pv.hasExpireMillis());

        // build the parameter archive
        buildParameterArchive("2019-04-06T00:00:00", "2019-04-06T03:00:00");

        start = Instant.parse("2019-04-06T00:00:00Z");
        stop = Instant.parse("2019-04-06T03:00:00Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/aggregate_para1.member2", start, stop).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(100, values.size());
        pv = values.get(0);
        engValue = pv.getEngValue();
        assertEquals(packetGenerator.paggr1_member2, engValue.getUint32Value());

        start = Instant.parse("2019-04-06T00:00:00Z");
        stop = Instant.parse("2019-04-06T02:00:00Z");
        List<Sample> samples = archiveClient.getSamples("/REFMDB/SUBSYS1/aggregate_para1.member3", start, stop).get();
        assertEquals(500, samples.size());
        Sample s0 = samples.get(0);
        assertEquals(2.72, s0.getAvg(), 1e-5);
    }

    @Test
    public void testWithArrayElements() throws Exception {
        generatePkt8("2019-04-06T20:00:00", 2 * 3600);

        // first two requests before the consolidation, should return data from cache
        Instant start = Instant.parse("2019-04-06T21:59:00Z");
        Instant stop = Instant.parse("2019-04-06T23:00:00Z");
        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/array_para1[5].member2", start, stop)
                .get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(59, values.size());
        org.yamcs.protobuf.Pvalue.ParameterValue pv = values.get(0);
        Value engValue = pv.getEngValue();
        assertEquals(10, engValue.getUint32Value());
        assertFalse(pv.hasExpireMillis());

        // build the parameter archive
        buildParameterArchive("2019-04-06T20:00:00", "2019-04-06T23:00:00");

        start = Instant.parse("2019-04-06T20:00:00Z");
        stop = Instant.parse("2019-04-06T23:00:00Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/array_para1[1].member3",
                start, stop, ListOptions.ascending(true)).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(100, values.size());
        pv = values.get(0);
        assertEquals("2019-04-06T20:00:00Z", Timestamps.toString(pv.getGenerationTime()));

        engValue = pv.getEngValue();
        assertEquals(0.5, engValue.getFloatValue(), 1e-5);

        start = Instant.parse("2019-04-06T20:00:00Z");
        stop = Instant.parse("2019-04-06T22:00:00Z");
        List<Sample> samples = archiveClient.getSamples("/REFMDB/SUBSYS1/array_para1[23].member1", start, stop).get();
        assertEquals(500, samples.size());
        Sample s0 = samples.get(0);
        assertEquals(23, s0.getAvg(), 1e-5);
    }

    @Test
    public void testWithFullArrayAggregates() throws Exception {
        generatePkt8("2021-05-17T20:00:00", 2 * 3600);

        // first two requests before the consolidation, should return data from cache
        Instant start = Instant.parse("2021-05-17T21:59:00Z");
        Instant stop = Instant.parse("2021-05-17T23:00:00Z");
        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/array_para1", start, stop)
                .get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(59, values.size());
        org.yamcs.protobuf.Pvalue.ParameterValue pv = values.get(0);
        Value engValue = pv.getEngValue().getArrayValue(5).getAggregateValue().getValue(1);

        assertEquals(10, engValue.getUint32Value());
        assertFalse(pv.hasExpireMillis());

        // build the parameter archive
        buildParameterArchive("2021-05-17T20:00:00", "2021-05-17T23:00:00");

        start = Instant.parse("2021-05-17T20:00:00Z");
        stop = Instant.parse("2021-05-17T23:00:00Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/array_para1",
                start, stop, ListOptions.ascending(true)).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(100, values.size());
        pv = values.get(0);

        assertEquals("2021-05-17T20:00:00Z", Timestamps.toString(pv.getGenerationTime()));

        engValue = pv.getEngValue().getArrayValue(1).getAggregateValue().getValue(2);
        assertEquals(0.5, engValue.getFloatValue(), 1e-5);

        stop = Instant.parse("2021-05-17T20:00:01Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/array_para1",
                start, stop, ListOptions.ascending(true)).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(1, values.size());

    }

    /**
     * PKT3 contains n*block constructs that generate multiple values for one parameter at the same timestamp
     */
    @Test
    public void testWithSameTimestamps() throws Exception {
        generatePkt3("2024-07-05T04:00:00", 100);

        Instant start = Instant.parse("2024-07-05T04:00:00Z");
        Instant stop = Instant.parse("2024-07-05T04:00:30Z");
        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/IntegerPara1_2", start, stop)
                .get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(60, values.size());
        org.yamcs.protobuf.Pvalue.ParameterValue pv0 = values.get(0);
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = values.get(1);

        assertEquals("2024-07-05T04:00:30Z", Timestamps.toString(pv0.getGenerationTime()));
        assertEquals("2024-07-05T04:00:30Z", Timestamps.toString(pv1.getGenerationTime()));

        assertEquals(4, pv0.getEngValue().getUint32Value());
        assertEquals(3, pv1.getEngValue().getUint32Value());

        // build the parameter archive
        buildParameterArchive("2024-07-05T04:00:00", "2024-07-05T06:00:00");

        start = Instant.parse("2024-07-05T04:00:02Z");
        stop = Instant.parse("2024-07-05T04:00:10Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/IntegerPara1_2",
                start, stop, ListOptions.ascending(true)).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(16, values.size());
        pv0 = values.get(0);
        pv1 = values.get(1);

        assertEquals("2024-07-05T04:00:02Z", Timestamps.toString(pv0.getGenerationTime()));
        assertEquals("2024-07-05T04:00:02Z", Timestamps.toString(pv1.getGenerationTime()));

        assertEquals(3, pv0.getEngValue().getUint32Value());
        assertEquals(4, pv1.getEngValue().getUint32Value());

        start = Instant.parse("2024-07-05T04:00:02Z");
        stop = Instant.parse("2024-07-05T04:00:10Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/IntegerPara1_2",
                start, stop, ListOptions.ascending(false)).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(16, values.size());
        pv0 = values.get(0);
        pv1 = values.get(1);

        assertEquals("2024-07-05T04:00:10Z", Timestamps.toString(pv0.getGenerationTime()));
        assertEquals("2024-07-05T04:00:10Z", Timestamps.toString(pv1.getGenerationTime()));

        assertEquals(4, pv0.getEngValue().getUint32Value());
        assertEquals(3, pv1.getEngValue().getUint32Value());
    }


    @Test
    public void testAggregatesWithSameTimestamps() throws Exception {
        generatePkt3("2024-07-05T14:00:00", 100);

        Instant start = Instant.parse("2024-07-05T14:00:00Z");
        Instant stop = Instant.parse("2024-07-05T14:00:30Z");
        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/aggregate_para2", start, stop)
                .get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(60, values.size());
        org.yamcs.protobuf.Pvalue.ParameterValue pv0 = values.get(0);
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = values.get(1);

        assertEquals("2024-07-05T14:00:30Z", Timestamps.toString(pv0.getGenerationTime()));
        assertEquals("2024-07-05T14:00:30Z", Timestamps.toString(pv1.getGenerationTime()));

        assertEquals(16, pv0.getEngValue().getAggregateValue().getValue(0).getUint32Value());
        assertEquals(16.5, pv0.getEngValue().getAggregateValue().getValue(1).getFloatValue());

        assertEquals(15, pv1.getEngValue().getAggregateValue().getValue(0).getUint32Value());
        assertEquals(15.5, pv1.getEngValue().getAggregateValue().getValue(1).getFloatValue());

        // build the parameter archive
        buildParameterArchive("2024-07-05T14:00:00", "2024-07-05T16:00:00");

        start = Instant.parse("2024-07-05T14:00:02Z");
        stop = Instant.parse("2024-07-05T14:00:10Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/aggregate_para2",
                start, stop, ListOptions.ascending(true)).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(16, values.size());
        pv0 = values.get(0);
        pv1 = values.get(1);

        assertEquals("2024-07-05T14:00:02Z", Timestamps.toString(pv0.getGenerationTime()));
        assertEquals("2024-07-05T14:00:02Z", Timestamps.toString(pv1.getGenerationTime()));

        assertEquals(15, pv0.getEngValue().getAggregateValue().getValue(0).getUint32Value());
        assertEquals(15.5, pv0.getEngValue().getAggregateValue().getValue(1).getFloatValue());

        assertEquals(16, pv1.getEngValue().getAggregateValue().getValue(0).getUint32Value());
        assertEquals(16.5, pv1.getEngValue().getAggregateValue().getValue(1).getFloatValue());

        start = Instant.parse("2024-07-05T14:00:02Z");
        stop = Instant.parse("2024-07-05T14:00:10Z");
        page = archiveClient.listValues("/REFMDB/SUBSYS1/aggregate_para2",
                start, stop, ListOptions.ascending(false)).get();

        values.clear();
        page.iterator().forEachRemaining(values::add);

        assertEquals(16, values.size());
        pv0 = values.get(0);
        pv1 = values.get(1);

        assertEquals("2024-07-05T14:00:10Z", Timestamps.toString(pv0.getGenerationTime()));
        assertEquals("2024-07-05T14:00:10Z", Timestamps.toString(pv1.getGenerationTime()));

        assertEquals(16, pv0.getEngValue().getAggregateValue().getValue(0).getUint32Value());
        assertEquals(16.5, pv0.getEngValue().getAggregateValue().getValue(1).getFloatValue());

        assertEquals(15, pv1.getEngValue().getAggregateValue().getValue(0).getUint32Value());
        assertEquals(15.5, pv1.getEngValue().getAggregateValue().getValue(1).getFloatValue());
    }

    private void buildParameterArchive(String start, String stop) throws InterruptedException, ExecutionException {
        ParameterArchive parameterArchive = YamcsServer.getServer().getService(yamcsInstance, ParameterArchive.class);
        Future<?> f = parameterArchive.reprocess(TimeEncoding.parse(start), TimeEncoding.parse(stop));
        f.get();
    }
}
