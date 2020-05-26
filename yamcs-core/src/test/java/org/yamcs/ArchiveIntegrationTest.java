package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.client.BulkRestDataSender;
import org.yamcs.client.ClientException;
import org.yamcs.client.ClientException.ExceptionData;
import org.yamcs.client.HttpClient;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.events.StreamEventProducer;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.AlarmSeverity;
import org.yamcs.protobuf.AlarmType;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.EditProcessorRequest;
import org.yamcs.protobuf.IndexResponse;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.StreamPacketIndexRequest;
import org.yamcs.protobuf.StreamParameterIndexRequest;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.protobuf.Table.Row.Cell;
import org.yamcs.protobuf.Table.TableData;
import org.yamcs.protobuf.Table.WriteRowsExceptionDetail;
import org.yamcs.protobuf.Table.WriteRowsResponse;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueHelper;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.util.Timestamps;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;

public class ArchiveIntegrationTest extends AbstractIntegrationTest {

    ColumnSerializer<Integer> csint = ColumnSerializerFactory.getBasicColumnSerializer(DataType.INT);
    ColumnSerializer<String> csstr = ColumnSerializerFactory.getBasicColumnSerializer(DataType.STRING);

    static { // to avoid getting the warning in the console in the test below that loads invalid table records
        Logger.getLogger("org.yamcs.yarch").setLevel(Level.SEVERE);
    }

    @Test
    public void testReplay() throws Exception {
        /*
         * Generate some realtime data (processor: realtime).
         * Then create a replay within the previous range of data (processor: testReplay).
         */
        generatePkt13AndPps("2015-01-01T10:00:00", 300);

        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .setInstance("IntegrationTest")
                .setName("testReplay")
                .setPersistent(true) // TODO temp
                .setType("Archive")
                .setConfig("{\"utcStart\": \"2015-01-01T10:01:00\", \"utcStop\": \"2015-01-01T10:05:00\"}")
                .build();
        restClient.doRequest("/processors", HttpMethod.POST, prequest).get();
        Thread.sleep(2000);

        /*
         * Listen to these parameters against testReplay.
         */
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("testReplay")
                .setSendFromCache(false)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_uint"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_double"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_enum_nc"))
                .build();
        subscription.sendMessage(request);

        // Give Yamcs some time to establish the subscription.
        Thread.sleep(2000);

        /*
         * Pause the replay.
         */
        EditProcessorRequest editRequest = EditProcessorRequest.newBuilder()
                .setState("paused")
                .build();
        restClient.doRequest("/processors/IntegrationTest/testReplay", HttpMethod.PATCH, editRequest).get();
        captor.clear();
        captor.assertSilence();

        /*
         * Now seek to the beginning (this also starts it)
         */
        editRequest = EditProcessorRequest.newBuilder()
                .setSeek(Timestamps.parse("2015-01-01T10:01:00Z"))
                .build();
        restClient.doRequest("/processors/IntegrationTest/testReplay", HttpMethod.PATCH, editRequest).get();

        /*
         * Verify the delivery on testReplay
         */
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(2, values.size());
        ParameterValue p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals("2015-01-01T10:01:00.000Z", p1_1_6.getGenerationTimeUTC());

        values = captor.expectTimely();
        assertEquals(3, values.size());
        ParameterValue pp_para_uint = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals("2015-01-01T10:01:00.010Z", pp_para_uint.getGenerationTimeUTC());

        ParameterValue pp_para_enum_nc = values.get(1);
        assertEquals("/REFMDB/SUBSYS1/processed_para_enum_nc", pp_para_enum_nc.getId().getName());
        assertEquals("2015-01-01T10:01:00.010Z", pp_para_uint.getGenerationTimeUTC());
        assertEquals(1, pp_para_enum_nc.getRawValue().getUint32Value());
        assertEquals("one_why not", pp_para_enum_nc.getEngValue().getStringValue());

        ParameterValue pp_para_double = values.get(2);
        assertEquals("/REFMDB/SUBSYS1/processed_para_double", pp_para_double.getId().getName());
        assertEquals("2015-01-01T10:01:00.010Z", pp_para_uint.getGenerationTimeUTC());

        values = captor.expectTimely();
        assertEquals(1, values.size());
        pp_para_uint = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals("2015-01-01T10:01:00.030Z", pp_para_uint.getGenerationTimeUTC());

        values = captor.expectTimely();
        assertEquals(2, values.size());
        p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals("2015-01-01T10:01:01.000Z", p1_1_6.getGenerationTimeUTC());
    }

    @Test
    public void testReplayWithTm2() throws Exception {
        generatePkt1AndTm2Pkt1("2019-01-01T10:00:00", 300);

        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .setInstance("IntegrationTest")
                .setName("testReplayWithTm2")
                .setPersistent(true) // TODO temp
                .setType("Archive")
                .setConfig("{\"utcStart\": \"2019-01-01T10:01:00\", \"utcStop\": \"2019-01-01T10:05:00\"}")
                .build();
        restClient.doRequest("/processors", HttpMethod.POST, prequest).get();
        Thread.sleep(2000);

        /*
         * Listen to these parameters against testReplayWithTm2.
         */
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("testReplayWithTm2")
                .setSendFromCache(false)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/tm2_para1"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/col-packet_id"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .build();
        subscription.sendMessage(request);

        // Give Yamcs some time to establish the subscription.
        Thread.sleep(2000);

        /*
         * Pause the replay.
         */
        EditProcessorRequest editRequest = EditProcessorRequest.newBuilder()
                .setState("paused")
                .build();
        restClient.doRequest("/processors/IntegrationTest/testReplayWithTm2", HttpMethod.PATCH, editRequest).get();
        captor.clear();
        captor.assertSilence();

        /*
         * Now seek to the beginning (this also starts it)
         */
        editRequest = EditProcessorRequest.newBuilder()
                .setSeek(Timestamps.parse("2019-01-01T10:01:00Z"))
                .build();
        restClient.doRequest("/processors/IntegrationTest/testReplayWithTm2", HttpMethod.PATCH, editRequest).get();

        List<ParameterValue> values = captor.expectTimely();

        assertEquals(1, values.size());
        ParameterValue pv1 = values.get(0);
        assertEquals("/REFMDB/tm2_para1", pv1.getId().getName());
        assertEquals("2019-01-01T10:01:00.000Z", pv1.getGenerationTimeUTC());
        assertEquals(20, pv1.getEngValue().getUint32Value());

        values = captor.expectTimely();
        assertEquals(2, values.size());

        ParameterValue pv2 = values.get(0);
        assertEquals("/REFMDB/col-packet_id", pv2.getId().getName());
        assertEquals("2019-01-01T10:01:00.000Z", pv2.getGenerationTimeUTC());

        ParameterValue pv3 = values.get(1);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_7", pv3.getId().getName());
        assertEquals("2019-01-01T10:01:00.000Z", pv3.getGenerationTimeUTC());
        assertEquals(packetGenerator.pIntegerPara1_1_7, pv3.getEngValue().getUint32Value());
    }

    @Test
    public void testReplayWithPpExclusion() throws Exception {
        generatePkt13AndPps("2015-02-01T10:00:00", 300);

        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .setInstance("IntegrationTest")
                .setName("testReplayWithPpExclusion")
                .setType("ArchiveWithPpExclusion")
                .setPersistent(true) // TODO temp
                .setConfig("{\"utcStart\": \"2015-02-01T10:01:00\", \"utcStop\": \"2015-02-01T10:05:00\"}")
                .build();
        restClient.doRequest("/processors", HttpMethod.POST, prequest).get();
        Thread.sleep(2000);

        /*
         * Listen to these parameters against testReplayWithPpExclusion.
         */
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("testReplayWithPpExclusion")
                .setSendFromCache(false)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_uint"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_double"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_enum_nc"))
                .build();
        subscription.sendMessage(request);

        // Give Yamcs some time to establish the subscription.
        Thread.sleep(2000);

        /*
         * Pause the replay.
         */
        EditProcessorRequest editRequest = EditProcessorRequest.newBuilder()
                .setState("paused")
                .build();
        restClient.doRequest("/processors/IntegrationTest/testReplayWithPpExclusion", HttpMethod.PATCH, editRequest)
                .get();
        captor.clear();
        captor.assertSilence();

        /*
         * Now seek to the beginning (this also starts it)
         */
        editRequest = EditProcessorRequest.newBuilder()
                .setSeek(Timestamps.parse("2015-02-01T10:01:00Z"))
                .build();
        restClient.doRequest("/processors/IntegrationTest/testReplayWithPpExclusion", HttpMethod.PATCH, editRequest)
                .get();

        List<ParameterValue> values = captor.expectTimely();

        assertEquals(2, values.size());
        ParameterValue p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals("2015-02-01T10:01:00.000Z", p1_1_6.getGenerationTimeUTC());

        values = captor.expectTimely();
        assertEquals(1, values.size());
        ParameterValue pp_para_uint = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/processed_para_uint", pp_para_uint.getId().getName());
        assertEquals("2015-02-01T10:01:00.030Z", pp_para_uint.getGenerationTimeUTC());

        values = captor.expectTimely();

        assertEquals(2, values.size());
        p1_1_6 = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1_1_6.getId().getName());
        assertEquals("2015-02-01T10:01:01.000Z", p1_1_6.getGenerationTimeUTC());
    }

    @Test
    public void testReplayLocalParams() throws Exception {
        Instant fmg = Instant.ofEpochMilli(System.currentTimeMillis() - 5 * 60 * 1000);
        byte[] respDl = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/LocalParaWithInitialValue1?start=" + fmg.toString()
                        + "&source=replay&limit=3",
                HttpMethod.GET).get();
        ListParameterHistoryResponse pdata = ListParameterHistoryResponse.parseFrom(respDl);
        assertEquals(1, pdata.getParameterCount());
        ParameterValue pv = pdata.getParameter(0);
        assertEquals(3.14, pv.getEngValue().getFloatValue(), 1e-5);

        Value v = ValueHelper.newValue((float) 6.62);
        restClient
                .doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalParaWithInitialValue1",
                        HttpMethod.POST, v)
                .get();
        Thread.sleep(1000);

        respDl = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/LocalParaWithInitialValue1?start=" + fmg.toString()
                        + "&source=replay&limit=3&order=asc",
                HttpMethod.GET).get();
        pdata = ListParameterHistoryResponse.parseFrom(respDl);
        assertEquals(2, pdata.getParameterCount());
        pv = pdata.getParameter(0);
        assertEquals(3.14, pv.getEngValue().getFloatValue(), 1e-5);
        pv = pdata.getParameter(1);
        assertEquals(6.62, pv.getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testEmptyIndex() throws Exception {
        String response = restClient
                .doRequest("/archive/IntegrationTest/packet-index?start=2035-01-02T00:00:00", HttpMethod.GET, "")
                .get();
        assertTrue(response.isEmpty());
    }

    @Test
    public void testIndexWithRestClient() throws Exception {
        generatePkt13AndPps("2015-02-01T10:00:00", 3600);

        restClient.setAcceptMediaType(HttpClient.MT_PROTOBUF);

        long start = TimeEncoding.parse("2015-02-01T00:00:00");
        long stop = TimeEncoding.parse("2015-02-01T11:00:00");
        StreamPacketIndexRequest packetOptions = StreamPacketIndexRequest.newBuilder()
                .setStart(TimeEncoding.toProtobufTimestamp(start))
                .setStop(TimeEncoding.toProtobufTimestamp(stop))
                .build();
        String resource = "/archive/IntegrationTest:streamPacketIndex";
        MyBulkReceiver mbr = new MyBulkReceiver();
        restClient.doBulkRequest(HttpMethod.POST, resource, packetOptions.toByteArray(), mbr).get();

        assertEquals(4, mbr.dist.size());

        StreamParameterIndexRequest ppOptions = StreamParameterIndexRequest.newBuilder()
                .setStart(TimeEncoding.toProtobufTimestamp(start))
                .setStop(TimeEncoding.toProtobufTimestamp(stop))
                .build();
        resource = "/archive/IntegrationTest:streamParameterIndex";
        mbr = new MyBulkReceiver();
        restClient.doBulkRequest(HttpMethod.POST, resource, ppOptions.toByteArray(), mbr).get();

        assertEquals(4, mbr.dist.size());
    }

    @Test
    public void testParameterHistory() throws Exception {
        generatePkt13AndPps("2015-02-02T10:00:00", 3600);
        byte[] respDl = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/ccsds-apid?start=2015-02-02T10:10:00&norepeat=true&limit=3",
                HttpMethod.GET).get();

        ListParameterHistoryResponse pdata = ListParameterHistoryResponse.parseFrom(respDl);
        assertEquals(1, pdata.getParameterCount());
        ParameterValue pv = pdata.getParameter(0);
        assertEquals(995, pv.getEngValue().getUint32Value());

        respDl = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/ccsds-apid?start=2015-02-02T10:10:00&norepeat=false&limit=3",
                HttpMethod.GET).get();
        pdata = ListParameterHistoryResponse.parseFrom(respDl);
        assertEquals(3, pdata.getParameterCount());
    }

    @Test
    public void testTableLoadDump() throws Exception {
        BulkRestDataSender brds = initiateTableLoad("table0");

        for (int i = 0; i < 100; i += 4) {
            ByteBuf buf = encode(getRecord(i), getRecord(i + 1), getRecord(i + 2), getRecord(i + 3));
            brds.sendData(buf);
        }
        WriteRowsResponse tlr = WriteRowsResponse.parseFrom(brds.completeRequest().get());
        assertEquals(100, tlr.getCount());

        verifyRecords("table0", 100);
        verifyRecordsDumpFormat("table0", 100);
    }

    @Test
    public void testTokenizedHistoIndexViaRest() throws Exception {
        generatePkt13AndPps("2015-02-03T10:00:00", 120);
        generatePkt13AndPps("2015-02-03T10:03:00", 100);

        // first without a limit
        byte[] resp = restClient
                .doRequest("/archive/IntegrationTest/packet-index?start=2015-02-03T00:00:00&stop=2015-02-03T11:00:00",
                        HttpMethod.GET)
                .get();

        IndexResponse ir = IndexResponse.parseFrom(resp);
        assertEquals(2, ir.getGroupCount());
        assertEquals(2, ir.getGroup(0).getEntryCount());
        assertEquals(2, ir.getGroup(1).getEntryCount());
        assertFalse(ir.hasContinuationToken());

        // now 1+3
        resp = restClient.doRequest(
                "/archive/IntegrationTest/packet-index?start=2015-02-03T00:00:00&stop=2015-02-03T11:00:00&limit=1",
                HttpMethod.GET).get();
        ir = IndexResponse.parseFrom(resp);
        assertEquals(1, ir.getGroupCount());
        assertEquals(1, ir.getGroup(0).getEntryCount());
        assertTrue(ir.hasContinuationToken());

        resp = restClient.doRequest(
                "/archive/IntegrationTest/packet-index?start=2015-02-03T00:00:00&stop=2015-02-03T11:00:00&limit=4&next="
                        + ir.getContinuationToken(),
                HttpMethod.GET).get();
        ir = IndexResponse.parseFrom(resp);
        assertEquals(2, ir.getGroupCount());
        assertEquals(1, ir.getGroup(0).getEntryCount());
        assertEquals(2, ir.getGroup(1).getEntryCount());
        assertFalse(ir.hasContinuationToken());
    }

    @Test
    public void testTokenizedCompletenessIndexViaRest() throws Exception {
        generatePkt13AndPps("2015-02-04T10:00:00", 120);
        packetGenerator.simulateGap(995);
        generatePkt13AndPps("2015-02-04T10:03:00", 100);

        // first without a limit
        byte[] resp = restClient.doRequest(
                "/archive/IntegrationTest/completeness-index?start=2015-02-04T00:00:00&stop=2015-02-04T11:00:00",
                HttpMethod.GET).get();
        IndexResponse ir = IndexResponse.parseFrom(resp);
        assertEquals(1, ir.getGroupCount());
        assertEquals(2, ir.getGroup(0).getEntryCount());
        assertFalse(ir.hasContinuationToken());

        // now 1+3
        resp = restClient.doRequest(
                "/archive/IntegrationTest/completeness-index?start=2015-02-04T00:00:00&stop=2015-02-04T11:00:00&limit=1",
                HttpMethod.GET).get();
        ir = IndexResponse.parseFrom(resp);
        assertEquals(1, ir.getGroupCount());
        assertEquals(1, ir.getGroup(0).getEntryCount());
        assertTrue(ir.hasContinuationToken());

        resp = restClient.doRequest(
                "/archive/IntegrationTest/completeness-index?start=2015-02-04T00:00:00&stop=2015-02-04T11:00:00&limit=4&next="
                        + ir.getContinuationToken(),
                HttpMethod.GET).get();
        ir = IndexResponse.parseFrom(resp);
        assertEquals(1, ir.getGroupCount());
        assertEquals(1, ir.getGroup(0).getEntryCount());
        assertFalse(ir.hasContinuationToken());
    }

    @Test
    @Ignore("Java client does not consistently read all received data after a sudden close, causing the exception from the server to be discarded")
    public void testTableLoadWithInvalidRecord() throws Exception {
        Throwable t1 = null;
        BulkRestDataSender brds = initiateTableLoad("table1");
        try {
            for (int i = 0; i < 100; i++) {
                Row tr;
                if (i != 50) {
                    tr = getRecord(i);
                } else {
                    Row.Builder trb = Row.newBuilder();
                    trb.addCells(Cell.newBuilder().setColumnId(2)
                            .setData(ByteString.copyFrom(csstr.toByteArray("test " + i))).build());
                    tr = trb.build();
                }
                brds.sendData(encode(tr));
            }
            brds.completeRequest().get();
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
    @Ignore("Java client does not consistently read all received data after a sudden close, causing the exception from the server to be discarded")
    public void testTableLoadWithInvalidRecord2() throws Exception {
        BulkRestDataSender brds = initiateTableLoad("table2");
        Throwable t1 = null;

        try {
            for (int i = 0; i < 100; i++) {
                ByteBuf buf = Unpooled.buffer();
                if (i != 50) {
                    buf = encode(getRecord(i));
                } else {
                    buf = Unpooled.wrappedBuffer(new byte[] { 3, 4, 3, 4 });
                }
                brds.sendData(buf);
            }
            brds.completeRequest().get();
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
        Event e1 = Event.newBuilder().setSource("IntegrationTest").setType("Event-Alarm-Test")
                .setSeverity(EventSeverity.WARNING).setSeqNumber(1)
                .setGenerationTime(TimeEncoding.parse("2019-05-12T11:15:00"))
                .setMessage("event1").build();
        sep.sendEvent(e1);

        Event e2 = e1.toBuilder().setSeverity(EventSeverity.CRITICAL).setSeqNumber(2)
                .setGenerationTime(TimeEncoding.parse("2019-05-12T11:15:00"))
                .setMessage("event2").build();
        sep.sendEvent(e2);

        byte[] resp = restClient.doRequest(
                "/archive/IntegrationTest/alarms?start=2019-05-12T11:00:00&stop=2019-05-12T12:00:00",
                HttpMethod.GET).get();

        ListAlarmsResponse listalarm = ListAlarmsResponse.parseFrom(resp);
        assertEquals(1, listalarm.getAlarmsCount());
        AlarmData alarm = listalarm.getAlarms(0);
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

    private void verifyRecords(String tblName, int n) throws Exception {
        CompletableFuture<byte[]> cf1 = restClient
                .doRequest("/archive/IntegrationTest/tables/" + tblName + "/data", HttpMethod.GET);
        byte[] data = cf1.get();
        TableData tableData = TableData.parseFrom(data);
        assertEquals(n, tableData.getRecordCount());
    }

    private void verifyRecordsDumpFormat(String tblName, int n) throws Exception {
        List<Row> trList = new ArrayList<>();
        CompletableFuture<Void> cf1 = restClient
                .doBulkRequest(HttpMethod.POST, "/archive/IntegrationTest/tables/" + tblName + ":readRows", data -> {
                    Row tr;
                    try {
                        tr = Row.parseFrom(data);
                        trList.add(tr);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });

        cf1.get();
        assertEquals(n, trList.size());
    }

    private BulkRestDataSender initiateTableLoad(String tblName) throws Exception {
        createTable(tblName);
        String resource = "/archive/IntegrationTest/tables/" + tblName + ":writeRows";
        CompletableFuture<BulkRestDataSender> cf = restClient
                .doBulkSendRequest(resource, HttpMethod.POST);
        BulkRestDataSender brds = cf.get();

        return brds;
    }

    private void createTable(String tblName) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        TupleDefinition td = new TupleDefinition();
        td.addColumn("a1", DataType.INT);
        td.addColumn("a2", DataType.STRING);

        TableDefinition tblDef = new TableDefinition(tblName, td, Arrays.asList("a1"));
        ydb.createTable(tblDef);

    }

    ByteBuf encode(MessageLite... msgl) throws IOException {
        ByteBuf buf = Unpooled.buffer();
        try (ByteBufOutputStream bufstream = new ByteBufOutputStream(buf)) {
            for (MessageLite msg : msgl) {
                msg.writeDelimitedTo(bufstream);
            }
        }
        return buf;
    }
}
