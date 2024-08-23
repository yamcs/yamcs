package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.ClientException.ExceptionData;
import org.yamcs.client.Page;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.archive.ArchiveClient.ListOptions;
import org.yamcs.client.archive.ArchiveClient.TableLoader;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.events.StreamEventProducer;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.AlarmSeverity;
import org.yamcs.protobuf.AlarmType;
import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.IndexEntry;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.protobuf.Table.Row.Cell;
import org.yamcs.protobuf.Table.TableData.TableRecord;
import org.yamcs.protobuf.Table.WriteRowsExceptionDetail;
import org.yamcs.protobuf.Table.WriteRowsResponse;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueHelper;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.util.Timestamps;

public class ArchiveIntegrationTest extends AbstractIntegrationTest {

    private ColumnSerializer<Integer> csint = ColumnSerializerFactory.getBasicColumnSerializerV2(DataType.INT);
    private ColumnSerializer<String> csstr = ColumnSerializerFactory.getBasicColumnSerializerV2(DataType.STRING);

    private ArchiveClient archiveClient;
    private ProcessorClient realtime;

    static { // to avoid getting the warning in the console in the test below that loads invalid table records
        Logger.getLogger("org.yamcs.yarch").setLevel(Level.SEVERE);
    }

    @BeforeEach
    public void prepare() {
        archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
        realtime = yamcsClient.createProcessorClient(yamcsInstance, "realtime");
    }

    @Test
    public void testReplay() throws Exception {
        /*
         * Generate some realtime data (processor: realtime).
         * Then create a replay within the previous range of data (processor: testReplay).
         */
        generatePkt13AndPps("2015-01-01T10:00:00", 300);

        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .setInstance("instance1")
                .setName("testReplay")
                .setPersistent(true) // TODO temp
                .setType("Archive")
                .setConfig("{\"start\": \"2015-01-01T10:01:00Z\", \"stop\": \"2015-01-01T10:05:00Z\"}")
                .build();
        ProcessorClient replay = yamcsClient.createProcessor(prequest).get();
        Thread.sleep(2000);

        /*
         * Listen to these parameters on our replay.
         */
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(replay.getInstance())
                .setProcessor(replay.getProcessor())
                .setSendFromCache(false)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_uint"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_double"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_enum_nc"))
                .build();
        subscription.sendMessage(request);

        /*
         * Pause the replay.
         */
        replay.pause().get();

        // Give Yamcs some time to establish the subscription and empty the websocket of any message that might have
        // been pending
        Thread.sleep(2000);

        captor.clear();
        captor.assertSilence();

        /*
         * Now seek to the beginning (this also starts it)
         */
        replay.seek(Instant.parse("2015-01-01T10:01:00Z")).get();

        /*
         * Verify the delivery on testReplay
         */
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(2, values.size());
        ParameterValue p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals(Timestamps.parse("2015-01-01T10:01:00.000Z"), p1_1_6.getGenerationTime());

        values = captor.expectTimely();
        assertEquals(3, values.size());
        ParameterValue pp_para_uint = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals(Timestamps.parse("2015-01-01T10:01:00.010Z"), pp_para_uint.getGenerationTime());

        ParameterValue pp_para_enum_nc = values.get(1);
        assertEquals("/REFMDB/SUBSYS1/processed_para_enum_nc", pp_para_enum_nc.getId().getName());
        assertEquals(Timestamps.parse("2015-01-01T10:01:00.010Z"), pp_para_uint.getGenerationTime());
        assertEquals(1, pp_para_enum_nc.getRawValue().getUint32Value());
        assertEquals("one_why not", pp_para_enum_nc.getEngValue().getStringValue());

        ParameterValue pp_para_double = values.get(2);
        assertEquals("/REFMDB/SUBSYS1/processed_para_double", pp_para_double.getId().getName());
        assertEquals(Timestamps.parse("2015-01-01T10:01:00.010Z"), pp_para_uint.getGenerationTime());

        values = captor.expectTimely();
        assertEquals(1, values.size());
        pp_para_uint = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals(Timestamps.parse("2015-01-01T10:01:00.030Z"), pp_para_uint.getGenerationTime());

        values = captor.expectTimely();
        assertEquals(2, values.size());
        p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals(Timestamps.parse("2015-01-01T10:01:01.000Z"), p1_1_6.getGenerationTime());
    }

    @Test
    public void testReplayWithTm2() throws Exception {
        generatePkt1AndTm2Pkt1("2019-01-01T10:00:00", 300);

        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .setInstance("instance1")
                .setName("testReplayWithTm2")
                .setPersistent(true) // TODO temp
                .setType("Archive")
                .setConfig("{\"start\": \"2019-01-01T10:01:00Z\", \"stop\": \"2019-01-01T10:05:00Z\"}")
                .build();

        ProcessorClient replay = yamcsClient.createProcessor(prequest).get();
        Thread.sleep(2000);

        /*
         * Listen to these parameters against testReplayWithTm2.
         */
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(replay.getInstance())
                .setProcessor(replay.getProcessor())
                .setSendFromCache(false)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/tm2_para1"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/col-packet_id"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .build();
        subscription.sendMessage(request);

        /*
         * Pause the replay.
         */
        replay.pause().get();

        // Give Yamcs some time to establish the subscription and empty the websocket of any message that might have
        // been pending
        Thread.sleep(2000);

        captor.clear();
        captor.assertSilence();

        /*
         * Now seek to the beginning (this also starts it)
         */
        replay.seek(Instant.parse("2019-01-01T10:01:00Z"));

        List<ParameterValue> values = captor.expectTimely();

        assertEquals(1, values.size());
        ParameterValue pv1 = values.get(0);
        assertEquals("/REFMDB/tm2_para1", pv1.getId().getName());
        assertEquals(Timestamps.parse("2019-01-01T10:01:00.000Z"), pv1.getGenerationTime());
        assertEquals(20, pv1.getEngValue().getUint32Value());

        values = captor.expectTimely();
        assertEquals(2, values.size());

        ParameterValue pv2 = values.get(0);
        assertEquals("/REFMDB/col-packet_id", pv2.getId().getName());
        assertEquals(Timestamps.parse("2019-01-01T10:01:00.000Z"), pv2.getGenerationTime());

        ParameterValue pv3 = values.get(1);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_7", pv3.getId().getName());
        assertEquals(Timestamps.parse("2019-01-01T10:01:00.000Z"), pv3.getGenerationTime());
        assertEquals(packetGenerator.pIntegerPara1_1_7, pv3.getEngValue().getUint32Value());
    }

    @Test
    public void testReplayWithPpExclusion() throws Exception {
        generatePkt13AndPps("2015-02-01T10:00:00", 300);

        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .setInstance("instance1")
                .setName("testReplayWithPpExclusion")
                .setType("ArchiveWithPpExclusion")
                .setPersistent(true) // TODO temp
                .setConfig("{\"start\": \"2015-02-01T10:01:00Z\", \"stop\": \"2015-02-01T10:05:00Z\"}")
                .build();
        ProcessorClient replay = yamcsClient.createProcessor(prequest).get();
        Thread.sleep(2000);

        /*
         * Listen to these parameters against testReplayWithPpExclusion.
         */
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(replay.getInstance())
                .setProcessor(replay.getProcessor())
                .setSendFromCache(false)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_uint"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_double"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_enum_nc"))
                .build();
        subscription.sendMessage(request);

        /*
         * Pause the replay.
         */
        replay.pause().get();

        // Give Yamcs some time to establish the subscription and empty the websocket of any message that might have
        // been pending
        Thread.sleep(2000);

        captor.clear();
        captor.assertSilence();

        /*
         * Now seek to the beginning (this also starts it)
         */
        replay.seek(Instant.parse("2015-02-01T10:01:00Z")).get();

        List<ParameterValue> values = captor.expectTimely();

        assertEquals(2, values.size());
        ParameterValue p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals(Timestamps.parse("2015-02-01T10:01:00.000Z"), p1_1_6.getGenerationTime());

        values = captor.expectTimely();
        assertEquals(1, values.size());
        ParameterValue pp_para_uint = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals(Timestamps.parse("2015-02-01T10:01:00.030Z"), pp_para_uint.getGenerationTime());

        values = captor.expectTimely();

        assertEquals(2, values.size());
        p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals(Timestamps.parse("2015-02-01T10:01:01.000Z"), p1_1_6.getGenerationTime());
    }

    @Test
    public void testReplayLocalParams() throws Exception {
        Instant fmg = Instant.ofEpochMilli(System.currentTimeMillis() - 5 * 60 * 1000);

        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/SUBSYS1/LocalParaWithInitialValue1", fmg, null,
                ListOptions.source("replay"),
                ListOptions.limit(3))
                .get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(1, values.size());
        ParameterValue pv = values.get(0);
        assertEquals(3.14, pv.getEngValue().getFloatValue(), 1e-5);

        Value v = ValueHelper.newValue((float) 6.62);
        realtime.setValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue1", v);
        Thread.sleep(1000);

        page = archiveClient.listValues("/REFMDB/SUBSYS1/LocalParaWithInitialValue1", fmg, null,
                ListOptions.source("replay"),
                ListOptions.limit(3),
                ListOptions.ascending(true))
                .get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(2, values.size());
        pv = values.get(0);
        assertEquals(3.14, pv.getEngValue().getFloatValue(), 1e-5);
        pv = values.get(1);
        assertEquals(6.62, pv.getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testReplayAggregateAlgoOutput() throws Exception {
        generatePkt1AndTm2Pkt1("2022-06-01T10:00:00", 300);
        Page<ParameterValue> page = archiveClient
                .listValues("/REFMDB/SUBSYS1/AlgoJavaAggr4.member2", Instant.parse("2022-06-01T10:00:00Z"), null,
                        ListOptions.source("replay"),
                        ListOptions.limit(3))
                .get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(3, values.size());
        assertEquals(23, values.get(0).getEngValue().getUint32Value());
    }

    @Test
    public void testEmptyIndex() throws Exception {
        Instant start = Instant.parse("2035-01-02T00:00:00Z");
        Page<IndexGroup> page = archiveClient.listPacketIndex(start, null).get();
        List<IndexGroup> groups = new ArrayList<>();
        page.iterator().forEachRemaining(groups::add);
        assertTrue(groups.isEmpty());
    }

    @Test
    public void testStreamingIndex() throws Exception {
        generatePkt13AndPps("2015-02-01T10:00:00", 3600);

        Instant start = Instant.parse("2015-02-01T00:00:00Z");
        Instant stop = Instant.parse("2015-02-01T11:00:00Z");

        List<ArchiveRecord> received = new ArrayList<>();
        archiveClient.streamPacketIndex(received::add, start, stop).get();
        assertEquals(4, received.size());

        received = new ArrayList<>();
        archiveClient.streamPacketIndex(received::add, start, stop).get();
        assertEquals(4, received.size());
    }

    @Test
    public void testStreamValues() throws Exception {
        generatePkt13AndTm2Pkt1("2022-06-15T13:50:00", 120);
        Instant start = Instant.parse("2022-06-15T13:50:00Z");
        Instant stop = Instant.parse("2022-06-15T13:50:10Z");
        List<Map<String, ParameterValue>> l = new ArrayList<>();
        archiveClient.streamValues(Arrays.asList("/REFMDB/tm2_para2"), m -> l.add(m), start, stop).get();
        assertEquals(20, l.size());

        l.clear();
        // replay only data received originally via tm2_realtime, that will halve the number of parameters
        archiveClient.streamValues(Arrays.asList("/REFMDB/tm2_para2"),
                Arrays.asList("tm2_realtime"),
                m -> l.add(m), start, stop).get();
        assertEquals(10, l.size());
    }

    @Test
    public void testParameterHistory() throws Exception {
        generatePkt13AndPps("2015-02-02T10:00:00", 3600);

        Instant start = Instant.parse("2015-02-02T10:10:00Z");
        Page<ParameterValue> page = archiveClient.listValues("/REFMDB/ccsds-apid", start, null,
                ListOptions.noRepeat(true),
                ListOptions.limit(3)).get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(1, values.size());
        ParameterValue pv = values.get(0);
        assertEquals(995, pv.getEngValue().getUint32Value());

        page = archiveClient.listValues("/REFMDB/ccsds-apid", start, null,
                ListOptions.noRepeat(false),
                ListOptions.limit(3)).get();
        values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);
        assertEquals(3, values.size());
    }

    @Test
    public void testTableLoadDump() throws Exception {
        createTable("table0");

        TableLoader loader = archiveClient.createTableLoader("table0");

        for (int i = 0; i < 100; i++) {
            loader.send(getRecord(i));
        }

        WriteRowsResponse response = loader.complete().get();
        assertEquals(100, response.getCount());

        verifyRecords("table0", 100);
        verifyRecordsDumpFormat("table0", 100);
    }

    @Test
    public void testTokenizedHistoIndex() throws Exception {
        generatePkt13AndPps("2015-02-03T10:00:00", 120);
        generatePkt13AndPps("2015-02-03T10:03:00", 100);

        // first without a limit
        Instant start = Instant.parse("2015-02-03T00:00:00Z");
        Instant stop = Instant.parse("2015-02-03T11:00:00Z");
        Page<IndexGroup> page = archiveClient.listPacketIndex(start, stop).get();
        List<IndexGroup> groups = new ArrayList<>();
        page.iterator().forEachRemaining(groups::add);

        assertEquals(2, groups.size());

        Map<String, AtomicInteger> packetCounts1 = new HashMap<>();
        for (IndexGroup group : page) {
            String packet = group.getId().getName();
            packetCounts1.putIfAbsent(packet, new AtomicInteger());
            packetCounts1.get(packet).addAndGet(group.getEntryCount());
        }

        // now with pagination
        page = archiveClient.listPacketIndex(start, stop, ListOptions.limit(2)).get();
        Map<String, AtomicInteger> packetCounts2 = new HashMap<>();
        do {
            for (IndexGroup group : page) {
                String packet = group.getId().getName();
                packetCounts2.putIfAbsent(packet, new AtomicInteger());
                packetCounts2.get(packet).addAndGet(group.getEntryCount());
            }
            page = page.getNextPage().get();
        } while (page.hasNextPage());

        packetCounts1.forEach((k, v) -> assertEquals(v.get(), packetCounts2.get(k).get()));
    }

    @Test
    public void testTokenizedCompletenessIndex() throws Exception {
        generatePkt13AndPps("2015-02-04T10:00:00", 120);
        packetGenerator.simulateGap(995);
        generatePkt13AndPps("2015-02-04T10:03:00", 100);

        // first without a limit
        Instant start = Instant.parse("2015-02-04T00:00:00Z");
        Instant stop = Instant.parse("2015-02-04T11:00:00Z");
        Page<IndexGroup> page = archiveClient.listCompletenessIndex(start, stop).get();
        List<IndexGroup> groups = new ArrayList<>();
        page.iterator().forEachRemaining(groups::add);

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).getEntryCount());
        assertFalse(page.hasNextPage());
        int total = 0;
        for (IndexEntry entry : groups.get(0).getEntryList()) {
            total += entry.getCount();
        }
        assertEquals(440, total);

        // now with a limit
        page = archiveClient.listCompletenessIndex(start, stop, ListOptions.limit(1)).get();
        groups = new ArrayList<>();
        page.iterator().forEachRemaining(groups::add);
        total = 0;
        assertEquals(1, groups.size());
        for (IndexEntry entry : groups.get(0).getEntryList()) {
            total += entry.getCount();
        }
        assertTrue(page.hasNextPage());

        page = page.getNextPage().get();
        groups = new ArrayList<>();
        page.iterator().forEachRemaining(groups::add);
        assertEquals(1, groups.size());
        for (IndexEntry entry : groups.get(0).getEntryList()) {
            total += entry.getCount();
        }
        assertEquals(440, total);
    }

    @Test
    @Disabled("Java client does not consistently read all received data after a sudden close, causing the exception from the server to be discarded")
    public void testTableLoadWithInvalidRecord() throws Exception {
        createTable("table1");

        TableLoader loader = archiveClient.createTableLoader("table1");

        Throwable t1 = null;
        try {
            for (int i = 0; i < 100; i++) {
                if (i != 50) {
                    loader.send(getRecord(i));
                } else {
                    Row.Builder trb = Row.newBuilder();
                    trb.addCells(Cell.newBuilder()
                            .setColumnId(2)
                            .setData(ByteString.copyFrom(csstr.toByteArray("test " + i))));
                    loader.send(trb.build());
                }
            }
            loader.complete().get();
        } catch (ExecutionException e) {
            t1 = e.getCause();
        }
        assertNotNull(t1);
        assertEquals(ClientException.class, t1.getClass());
        ExceptionData excData = ((ClientException) t1).getDetail();

        assertTrue(excData.getDetail() != null);
        WriteRowsExceptionDetail detail = excData.getDetail().unpack(WriteRowsExceptionDetail.class);
        assertEquals(50, detail.getCount());
        verifyRecords("table1", 50);
    }

    @Test
    @Disabled("Java client does not consistently read all received data after a sudden close, causing the exception from the server to be discarded")
    public void testTableLoadWithInvalidRecord2() throws Exception {
        createTable("table2");

        TableLoader loader = archiveClient.createTableLoader("table2");
        Throwable t1 = null;
        try {
            for (int i = 0; i < 100; i++) {
                if (i != 50) {
                    loader.send(getRecord(i));
                } else {
                    Message invalidMessage = Event.newBuilder().setMessage("abc").build();
                    loader.send((Row) invalidMessage);
                }
            }
            loader.complete().get();
        } catch (ExecutionException e) {
            t1 = e.getCause();
        }
        assertNotNull(t1);
        t1.printStackTrace();
        assertTrue(t1 instanceof ClientException);
        ExceptionData excData = ((ClientException) t1).getDetail();

        assertTrue(excData.getDetail() != null);
        WriteRowsExceptionDetail detail = excData.getDetail().unpack(WriteRowsExceptionDetail.class);
        assertEquals(50, detail.getCount());
        verifyRecords("table2", 50);
    }

    @Test
    public void testRetrieveAlarmHistory() throws Exception {
        StreamEventProducer sep = new StreamEventProducer(yamcsInstance);
        Db.Event e1 = Db.Event.newBuilder().setSource("IntegrationTest").setType("Event-Alarm-Test")
                .setSeverity(EventSeverity.WARNING).setSeqNumber(1)
                .setGenerationTime(TimeEncoding.parse("2019-05-12T11:15:00"))
                .setMessage("event1").build();
        sep.sendEvent(e1);

        Db.Event e2 = e1.toBuilder().setSeverity(EventSeverity.CRITICAL).setSeqNumber(2)
                .setGenerationTime(TimeEncoding.parse("2019-05-12T11:15:00"))
                .setMessage("event2").build();
        sep.sendEvent(e2);

        Instant start = Instant.parse("2019-05-12T11:00:00Z");
        Instant stop = Instant.parse("2019-05-12T12:00:00Z");
        List<AlarmData> alarms = archiveClient.listAlarms(start, stop).get();

        assertEquals(1, alarms.size());
        AlarmData alarm = alarms.get(0);
        assertEquals(AlarmType.EVENT, alarm.getType());

        assertEquals("Event-Alarm-Test", alarm.getId().getName());
        assertEquals("/yamcs/event/IntegrationTest", alarm.getId().getNamespace());
        assertEquals(AlarmSeverity.CRITICAL, alarm.getSeverity());
    }

    private Row getRecord(int i) {
        // the column info is only required for the first record actually
        Row tr = Row.newBuilder()
                .addColumns(Row.ColumnInfo.newBuilder().setId(1).setName("a1").setType("INT"))
                .addColumns(Row.ColumnInfo.newBuilder().setId(2).setName("a2").setType("STRING"))
                .addCells(Cell.newBuilder().setColumnId(1).setData(ByteString.copyFrom(csint.toByteArray(i))))
                .addCells(Cell.newBuilder().setColumnId(2).setData(ByteString.copyFrom(csstr.toByteArray("test " + i))))
                .build();
        return tr;
    }

    private void verifyRecords(String table, int n) throws Exception {
        List<TableRecord> records = archiveClient.listRecords(table).get();
        assertEquals(n, records.size());
    }

    private void verifyRecordsDumpFormat(String table, int n) throws Exception {
        List<Row> trList = new ArrayList<>();
        archiveClient.dumpTable(table, trList::add).get();
        assertEquals(n, trList.size());
    }

    private void createTable(String tblName) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        TupleDefinition td = new TupleDefinition();
        td.addColumn("a1", DataType.INT);
        td.addColumn("a2", DataType.STRING);

        TableDefinition tblDef = new TableDefinition(tblName, td, Arrays.asList("a1"));
        ydb.createTable(tblDef);

    }
}
