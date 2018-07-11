package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.api.RestEventProducer;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.HttpClient;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterValueResponse;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Rest.IssueCommandResponse;
import org.yamcs.protobuf.Rest.ListServiceInfoResponse;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Web.ParameterSubscriptionRequest;
import org.yamcs.protobuf.Web.ParameterSubscriptionResponse;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.AggregateValue;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.RouteHandler;
import org.yamcs.web.websocket.ManagementResource;

import com.google.gson.JsonStreamParser;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

public class IntegrationTest extends AbstractIntegrationTest {

    @Ignore
    @Test
    public void testWsParameterSubscriPerformance() throws Exception {
        // subscribe to parameters
        long t0 = System.currentTimeMillis();
        ParameterSubscriptionRequest invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        wsClient.sendRequest(wsr);

        for (int i = 0; i < 1000000; i++) {
            packetGenerator.generate_PKT1_1();
        }
        System.out.println("total time: " + (System.currentTimeMillis() - t0));
    }

    @Test
    public void testWsParameter() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest invalidSubscrList = getSubscription(true, false,
                "/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/InvalidParaName");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        CompletableFuture<WebSocketReplyData> cf = wsClient.sendRequest(wsr);

        WebSocketReplyData wsrd = cf.get();
        assertTrue(wsrd.hasData());
        assertEquals(ParameterSubscriptionResponse.class.getSimpleName(), wsrd.getType());
        ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        assertEquals(1, psr.getInvalidCount());
        assertEquals("/REFMDB/SUBSYS1/InvalidParaName", psr.getInvalid(0).getName());

        // generate some TM packets and monitor realtime reception
        for (int i = 0; i < 10; i++) {
            packetGenerator.generate_PKT1_1();
        }
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pdata);
        checkPvals(pdata.getParameterList(), packetGenerator);

        ParameterSubscriptionRequest subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        wsr = new WebSocketRequest("parameter", "unsubscribe", subscrList);
        wsClient.sendRequest(wsr);

        // we subscribe again and should get the previous values from the cache
        wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        wsClient.sendRequest(wsr);
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        checkPvals(pdata.getParameterList(), packetGenerator);
    }

    @Test
    public void testWsParameterExpiration() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest req = getSubscription(false, true, "/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", req);
        wsClient.sendRequest(wsr).get();
        assertTrue(wsListener.parameterDataList.isEmpty());

        // generate a TM packets and monitor realtime reception
        packetGenerator.generate_PKT1_1();
        ParameterData pdata = wsListener.parameterDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(pdata);
        // assertEquals(2, pdata.getParameterCount());
        checkPvals(pdata.getParameterList(), packetGenerator);

        // after 1.5 sec we should get an set of expired parameters
        pdata = wsListener.parameterDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(pdata);
        assertEquals(2, pdata.getParameterCount());
        for (ParameterValue pv : pdata.getParameterList()) {
            assertEquals(AcquisitionStatus.EXPIRED, pv.getAcquisitionStatus());
        }
    }

    @Test
    public void testWsTime() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("time", "subscribe");
        wsClient.sendRequest(wsr);
        TimeInfo ti = wsListener.timeInfoList.poll(2, TimeUnit.SECONDS);
        assertNotNull(ti);
    }

    @Test
    public void testWsParameterUnsubscription() throws Exception {
        ParameterSubscriptionRequest.Builder subscr1 = ParameterSubscriptionRequest.newBuilder()
                .setSendFromCache(false);
        subscr1.addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build());
        subscr1.addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7").build());
        subscr1.addId(NamedObjectId.newBuilder().setNamespace("MDB:AliasParam").setName("para6alias").build());

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subscr1.build());
        WebSocketReplyData wsrd = wsClient.sendRequest(wsr).get();

        ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        int subscrId1 = psr.getSubscriptionId();

        packetGenerator.generate_PKT1_1();
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pdata);
        checkPvals(3, pdata.getParameterList(), packetGenerator);

        ParameterSubscriptionRequest subscrList = getSubscription(false, false, "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        wsr = new WebSocketRequest("parameter", "unsubscribe", subscrList);
        wsClient.sendRequest(wsr).get();
        packetGenerator.generate_PKT1_1();

        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        checkPvals(2, pdata.getParameterList(), packetGenerator);

        wsr = new WebSocketRequest("parameter", "unsubscribeAll",
                ParameterSubscriptionRequest.newBuilder().setSubscriptionId(subscrId1).build());
        wsClient.sendRequest(wsr).get();

        // we subscribe again and should get a different subscription id
        wsr = new WebSocketRequest("parameter", "subscribe", subscr1.build());
        wsrd = wsClient.sendRequest(wsr).get();

        psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        int subscrId2 = psr.getSubscriptionId();
        assertTrue(subscrId1 != subscrId2);
    }

    @Test
    public void testRestParameterGet() throws Exception {
        ////// gets parameters from cache via REST - first attempt with one invalid parameter
        ParameterSubscriptionRequest invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/InvalidParaName");
        BulkGetParameterValueRequest req = BulkGetParameterValueRequest.newBuilder().setFromCache(true)
                .addAllId(invalidSubscrList.getIdList()).build();

        try {
            restClient.doRequest("/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req))
                    .get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            String err = e.getMessage();
            assertTrue(err.contains("Invalid parameters"));
            assertTrue(err.contains("/REFMDB/SUBSYS1/InvalidParaName"));
        }

        packetGenerator.generate_PKT1_1();
        Thread.sleep(1000);
        /////// gets parameters from cache via REST - second attempt with valid parameters
        ParameterSubscriptionRequest validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getIdList())
                .build();

        String response = restClient
                .doRequest("/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req)).get();
        BulkGetParameterValueResponse bulkPvals = fromJson(response, BulkGetParameterValueResponse.newBuilder())
                .build();
        checkPvals(bulkPvals.getValueList(), packetGenerator);

        /////// gets parameters from via REST - waiting for update - first test the timeout in case no update is coming
        long t0 = System.currentTimeMillis();
        req = BulkGetParameterValueRequest.newBuilder()
                .setFromCache(false)
                .setTimeout(2000).addAllId(validSubscrList.getIdList()).build();

        Future<String> responseFuture = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/mget",
                HttpMethod.GET, toJson(req));

        bulkPvals = fromJson(responseFuture.get(), BulkGetParameterValueResponse.newBuilder()).build();
        long t1 = System.currentTimeMillis();
        assertEquals(2000, t1 - t0, 200);
        assertEquals(0, bulkPvals.getValueCount());
        //////// gets parameters from via REST - waiting for update - now with some parameters updated
        packetGenerator.pIntegerPara1_1_6 = 10;
        packetGenerator.pIntegerPara1_1_7 = 5;
        responseFuture = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET,
                toJson(req));
        Thread.sleep(1000); // wait to make sure that the data has reached the server

        packetGenerator.generate_PKT1_1();

        bulkPvals = fromJson(new String(responseFuture.get()), BulkGetParameterValueResponse.newBuilder()).build();

        checkPvals(bulkPvals.getValueList(), packetGenerator);
    }

    @Test
    public void testRestArrayAggregateParameterGet() throws Exception {
        packetGenerator.generate_PKT8();

        ParameterSubscriptionRequest subscrList = getSubscription("/REFMDB/SUBSYS1/array_para1");
        BulkGetParameterValueRequest req = BulkGetParameterValueRequest.newBuilder().setFromCache(true)
                .addAllId(subscrList.getIdList())
                .build();

        String response = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/mget",
                HttpMethod.GET, toJson(req)).get();
        BulkGetParameterValueResponse pvals = fromJson(response, BulkGetParameterValueResponse.newBuilder())
                .build();
        assertEquals(1, pvals.getValueCount());
        ParameterValue pv = pvals.getValue(0);
        Value v = pv.getEngValue();
        assertEquals(Value.Type.ARRAY, v.getType());

        Value v1 = v.getArrayValue(10);
        assertEquals(Value.Type.AGGREGATE, v1.getType());
        AggregateValue av = v1.getAggregateValue();
        assertEquals("member1", av.getName(0));
        assertEquals(5.0, av.getValue(2).getFloatValue(), 1e-5);
    }

    @Test
    public void testRestParameterSetInvalidParam() throws Exception {
        BulkSetParameterValueRequest.Builder bulkb = BulkSetParameterValueRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"));
        requestb.setValue(ValueHelper.newValue(3.14));
        bulkb.addRequest(requestb);

        try {
            restClient.doRequest("/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST,
                    toJson(bulkb.build())).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof YamcsApiException);
        }
    }

    @Test
    public void testRestParameterSetInvalidType() throws Exception {
        BulkSetParameterValueRequest.Builder bulkb = BulkSetParameterValueRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"));
        requestb.setValue(ValueHelper.newValue("blablab"));
        bulkb.addRequest(requestb);

        try {
            restClient.doRequest("/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST,
                    toJson(bulkb.build())).get();
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof YamcsApiException);
        }
    }

    @Test
    public void testRestParameterSet() throws Exception {
        BulkSetParameterValueRequest.Builder bulkb = BulkSetParameterValueRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"));
        requestb.setValue(ValueHelper.newValue(5));
        bulkb.addRequest(requestb);

        String resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST,
                toJson(bulkb.build())).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara1",
                HttpMethod.GET, "").get();
        ParameterValue pv = fromJson(resp, ParameterValue.newBuilder()).build();
        assertEquals(requestb.getValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSet2() throws Exception {
        // test simple set just for the value
        Value v = ValueHelper.newValue(3.14);
        String resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara2",
                HttpMethod.POST, toJson(v)).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara2",
                HttpMethod.GET, "").get();
        ParameterValue pv = fromJson(resp, ParameterValue.newBuilder()).build();
        assertEquals(v, pv.getEngValue());
    }

    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        // first subscribe to command history
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);
        wsListener.cmdHistoryDataList.clear();

        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        String resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq);
        assertTrue(resp.contains("binary"));

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", cmdid.getCommandName());
        assertEquals(5, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
    }

    /*-@Test
    public void testValidateCommand() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);
    
        ValidateCommandRequest cmdreq = getValidateCommand("/REFMDB/SUBSYS1/CRITICAL_TC1", 10, "p1", "2");
        String resp = doRequest("/commanding/validator", HttpMethod.POST, cmdreq, SchemaRest.ValidateCommandRequest.WRITE);
        ValidateCommandResponse vcr = (fromJson(resp, SchemaRest.ValidateCommandResponse.MERGE)).build();
        assertEquals(1, vcr.getCommandSignificanceCount());
        CommandSignificance significance = vcr.getCommandSignificance(0);
        assertEquals(10, significance.getSequenceNumber());
        assertEquals(SignificanceLevelType.CRITICAL, significance.getSignificance().getConsequenceLevel());
        assertEquals("this is a critical command, pay attention", significance.getSignificance().getReasonForWarning());
    
    }*/

    @Test
    public void testSendCommandFailedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        IssueCommandRequest cmdreq = getCommand(6, "p1", "2");
        String resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC1", HttpMethod.POST, cmdreq);
        assertTrue(resp.contains("binary"));

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.TransmissionContraints_KEY, cha.getName());
        assertEquals("NOK", cha.getValue().getStringValue());

        cmdhist = wsListener.cmdHistoryDataList.poll(1, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.CommandFailed_KEY, cha.getName());
        assertEquals("Transmission constraints check failed", cha.getValue().getStringValue());

        cmdhist = wsListener.cmdHistoryDataList.poll(1, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.CommandComplete_KEY, cha.getName());
        assertEquals("NOK", cha.getValue().getStringValue());
    }

    @Test
    public void testSendCommandSucceedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        IssueCommandRequest cmdreq = getCommand(6, "p1", "2");
        String resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC2", HttpMethod.POST, cmdreq);
        assertTrue(resp.contains("binary"));

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.TransmissionContraints_KEY, cha.getName());
        assertEquals("PENDING", cha.getValue().getStringValue());

        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
        assertNull(cmdhist);
        Value v = ValueHelper.newValue(true);
        restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/AllowCriticalTC2",
                HttpMethod.POST, toJson(v)).get();
        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(cmdhist);

        assertEquals(1, cmdhist.getAttrCount());

        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.TransmissionContraints_KEY, cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
    }

    @Test
    public void testUpdateCommandHistory() throws Exception {

        // Send a command a store its commandId
        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        String resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq);
        assertTrue(resp.contains("binary"));
        IssueCommandResponse commandResponse = fromJson(resp, IssueCommandResponse.newBuilder()).build();

        // insert two values in the command history
        CommandId commandId = commandResponse.getCommandQueueEntry().getCmdId();
        Rest.UpdateCommandHistoryRequest.Builder updateHistoryRequest = Rest.UpdateCommandHistoryRequest.newBuilder()
                .setCmdId(commandId);
        updateHistoryRequest.addHistoryEntry(
                Rest.UpdateCommandHistoryRequest.KeyValue.newBuilder().setKey("testKey1").setValue("testValue1"));
        updateHistoryRequest.addHistoryEntry(
                Rest.UpdateCommandHistoryRequest.KeyValue.newBuilder().setKey("testKey2").setValue("testValue2"));
        doRealtimeRequest("/commandhistory/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST,
                updateHistoryRequest.build());

        // Query command history and check that we can retreive the inserted values
        String respDl = restClient.doRequest("/archive/IntegrationTest/downloads/commands", HttpMethod.GET, "").get();
        List<CommandHistoryEntry> commandHistoryEntries = splitCommandHistoryEntries(respDl);
        List<CommandHistoryAttribute> commandHistoryAttributes = commandHistoryEntries
                .get(commandHistoryEntries.size() - 1).getAttrList();
        boolean foundKey1 = false, foundKey2 = false;
        for (CommandHistoryAttribute cha : commandHistoryAttributes) {
            if (cha.getName().equals("testKey1") &&
                    cha.getValue().getStringValue().equals("testValue1")) {
                foundKey1 = true;
            }
            if (cha.getName().equals("testKey2") &&
                    cha.getValue().getStringValue().equals("testValue2")) {
                foundKey2 = true;
            }
        }
        assertTrue(foundKey1);
        assertTrue(foundKey2);
    }

    // parses a series of messages (not really a list because they are not separated by "," and do not have start and
    // end of list ([ ])
    private List<CommandHistoryEntry> splitCommandHistoryEntries(String concatenatedJson) throws IOException {
        List<CommandHistoryEntry> r = new ArrayList<>();
        JsonStreamParser parser = new JsonStreamParser(concatenatedJson);
        while (parser.hasNext()) {
            String json = parser.next().toString();
            CommandHistoryEntry.Builder msgb = CommandHistoryEntry.newBuilder();
            JsonFormat.parser().merge(json, msgb);
            r.add(msgb.build());
        }
        return r;
    }

    @Test
    public void testWsManagement() throws Exception {
        ClientInfo cinfo = getClientInfo();
        assertEquals("IntegrationTest", cinfo.getInstance());
        assertEquals("realtime", cinfo.getProcessorName());
        assertEquals("it-junit", cinfo.getApplicationName());

        ProcessorInfo pinfo = getProcessorInfo();
        assertEquals("IntegrationTest", pinfo.getInstance());
        assertEquals("realtime", pinfo.getName());
        assertEquals("realtime", pinfo.getType());
        assertEquals("system", pinfo.getCreator());
    }

    /*
     * private ValidateCommandRequest getValidateCommand(String cmdName, int seq, String... args) { NamedObjectId cmdId
     * = NamedObjectId.newBuilder().setName(cmdName).build();
     * 
     * CommandType.Builder cmdb =
     * CommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq); for(int i =0
     * ;i<args.length; i+=2) {
     * cmdb.addArguments(ArgumentAssignmentType.newBuilder().setName(args[i]).setValue(args[i+1]).build()); }
     * 
     * return ValidateCommandRequest.newBuilder().addCommand(cmdb.build()).build(); }
     */

    private ProcessorInfo getProcessorInfo() throws InterruptedException {
        WebSocketRequest wsr = new WebSocketRequest("management", ManagementResource.OP_getProcessorInfo);
        wsClient.sendRequest(wsr);
        ProcessorInfo pinfo = wsListener.processorInfoList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pinfo);
        return pinfo;
    }

    // Keeping it D-R-Y. Could be refactored into httpClient to make writing short tests easier
    private <T extends Message> String doRealtimeRequest(String path, HttpMethod method, T msg) throws Exception {
        String json = toJson(msg);
        return restClient.doRequest("/processors/IntegrationTest/realtime" + path, method, json).get();
    }

    private void checkPvals(List<ParameterValue> pvals, RefMdbPacketGenerator packetProvider) {
        checkPvals(2, pvals, packetProvider);
    }

    private void checkPvals(int expectedNumParams, List<ParameterValue> pvals, RefMdbPacketGenerator packetProvider) {

        assertNotNull(pvals);

        assertEquals(expectedNumParams, pvals.size());

        for (ParameterValue p : pvals) {
            assertEquals(AcquisitionStatus.ACQUIRED, p.getAcquisitionStatus());
            Value praw = p.getRawValue();
            assertNotNull(praw);
            Value peng = p.getEngValue();

            if ("/REFMDB/SUBSYS1/IntegerPara1_1_6".equals(p.getId().getName())
                    || "para6alias".equals(p.getId().getName())) {
                assertEquals(Type.UINT32, praw.getType());
                assertEquals(packetProvider.pIntegerPara1_1_6, praw.getUint32Value());

                assertEquals(Type.UINT32, peng.getType());
                assertEquals(packetProvider.pIntegerPara1_1_6, peng.getUint32Value());

            } else if ("/REFMDB/SUBSYS1/IntegerPara1_1_7".equals(p.getId().getName())) {
                assertEquals(Type.UINT32, praw.getType());
                assertEquals(packetProvider.pIntegerPara1_1_7, praw.getUint32Value());

                assertEquals(Type.UINT32, peng.getType());
                assertEquals(packetProvider.pIntegerPara1_1_7, peng.getUint32Value());
            } else {
                fail("Unkonwn parameter " + p.getId());
            }
        }
    }

    @Test
    public void testChangeReplaySpeed() throws Exception {

        // generate some data
        for (int i = 0; i < 100; i++) {
            packetGenerator.generate_PKT1_1();
        }

        // sget client info
        ClientInfo ci = getClientInfo();
        String config = "{\n"
                + "  \"utcStart\": \"" + TimeEncoding.toString(0) + "\", \n"
                + "  \"utcStop\": \"" + TimeEncoding.toString(TimeEncoding.MAX_INSTANT) + "\", \n"
                + "  \"parameterRequest\": {\n"
                + "   \"nameFilter\": [{\"name\":\"/REFMDB/SUBSYS1/IntegerPara1_1_6\"}]\n"
                + "}\n"
                + "}";
        // Create replay
        Rest.CreateProcessorRequest cpr = Rest.CreateProcessorRequest.newBuilder()
                .setName("replay_test")
                .setType("Archive")
                .setConfig(config)
                .setPersistent(true)
                .addClientId(ci.getId()).build();
        String resp1 = restClient.doRequest("/processors/IntegrationTest", HttpMethod.POST, toJson(cpr)).get();

        assertEquals(resp1, "");

        // Check speed is 1.0
        ProcessorInfo pi1 = getProcessorInfo();
        Yamcs.ReplaySpeed speed1 = pi1.getReplayRequest().getSpeed();
        assertEquals(1.0f, speed1.getParam(), 1e-6);

        // Set replay speed to 2.0
        Rest.EditProcessorRequest epr = Rest.EditProcessorRequest.newBuilder()
                .setSpeed("2x").build();
        String resp2 = restClient.doRequest("/processors/IntegrationTest/replay_test", HttpMethod.POST, toJson(epr))
                .get();
        assertEquals(resp2, "");

        // Check speed is 2.0
        ProcessorInfo pi2 = getProcessorInfo();
        Yamcs.ReplaySpeed speed2 = pi2.getReplayRequest().getSpeed();
        assertEquals(speed2.getParam(), 2.0f, 1e-6);
        restClient.doRequest("/processors/IntegrationTest/replay_test", HttpMethod.DELETE).get();
        Thread.sleep(2000);
    }

    @Test
    public void testServicesStopStart() throws Exception {
        String serviceClass = "org.yamcs.archive.CommandHistoryRecorder";

        String resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET, "").get();
        ListServiceInfoResponse r = fromJson(resp, ListServiceInfoResponse.newBuilder()).build();
        assertEquals(9, r.getServiceList().size());

        ServiceInfo servInfo = r.getServiceList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());

        resp = restClient
                .doRequest("/services/IntegrationTest/" + serviceClass + "?state=STOPPED", HttpMethod.PATCH, "")
                .get();
        assertEquals("", resp);

        resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET, "").get();
        r = fromJson(resp, ListServiceInfoResponse.newBuilder()).build();
        servInfo = r.getServiceList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.TERMINATED, servInfo.getState());

        resp = restClient
                .doRequest("/services/IntegrationTest/" + serviceClass + "?state=running", HttpMethod.PATCH, "")
                .get();
        assertEquals("", resp);

        resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET, "").get();
        r = fromJson(resp, ListServiceInfoResponse.newBuilder()).build();
        servInfo = r.getServiceList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());
    }

    @Test
    public void testRestEvents() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("events", "subscribe");
        wsClient.sendRequest(wsr).get();

        RestEventProducer rep = new RestEventProducer(ycp);
        Event e1 = Event.newBuilder().setSource("IntegrationTest").setSeqNumber(1)
                .setReceptionTime(TimeEncoding.getWallclockTime()).setGenerationTime(TimeEncoding.getWallclockTime())
                .setMessage("event1").build();
        rep.sendEvent(e1);

        Event e2 = wsListener.eventList.poll(2, TimeUnit.SECONDS);
        assertNotNull(e2);
        assertEquals(e1.getGenerationTime(), e2.getGenerationTime());
        assertEquals(e1.getMessage(), e2.getMessage());
    }

    @Test
    public void testStaticFile() throws Exception {
        HttpClient httpClient = new HttpClient();
        File dir = new File("/tmp/yamcs-web/");
        dir.mkdirs();

        File file1 = File.createTempFile("test1_", null, dir);
        FileOutputStream file1Out = new FileOutputStream(file1);
        Random rand = new Random();
        byte[] b = new byte[1932];
        for (int i = 0; i < 20; i++) {
            rand.nextBytes(b);
            file1Out.write(b);
        }
        file1Out.close();

        File file2 = File.createTempFile("test2_", null, dir);
        FileOutputStream file2Out = new FileOutputStream(file2);

        httpClient.doBulkReceiveRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null,
                adminUsername, adminPassword, data -> {
                    try {
                        file2Out.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).get();
        file2Out.close();
        assertTrue(com.google.common.io.Files.equal(file1, file2));

        // test if not modified since
        SimpleDateFormat dateFormatter = new SimpleDateFormat(RouteHandler.HTTP_DATE_FORMAT);

        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(HttpHeaderNames.IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified()));
        YamcsApiException e1 = null;
        try {
            httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null,
                    adminUsername, adminPassword, httpHeaders).get();
        } catch (ExecutionException e) {
            e1 = (YamcsApiException) e.getCause();
        }
        assertNotNull(e1);
        assertTrue(e1.toString().contains("304"));

        httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(HttpHeaderNames.IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified() - 1000));
        byte[] b1 = httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null,
                adminUsername, adminPassword, httpHeaders).get();
        assertEquals(file1.length(), b1.length);

        file1.delete();
        file2.delete();
    }
}
