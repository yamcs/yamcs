package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterValueResponse;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.utils.HttpClient;
import org.yamcs.web.websocket.ManagementResource;

import com.google.protobuf.MessageLite;

import io.netty.handler.codec.http.HttpMethod;
import io.protostuff.Schema;


public class IntegrationTest extends AbstractIntegrationTest {
    
    @Ignore
    @Test
    public void testWsParameterSubscriPerformance() throws Exception {
        //subscribe to parameters
        long t0 = System.currentTimeMillis();
        NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        wsClient.sendRequest(wsr);

        for (int i=0;i <1000000; i++) packetGenerator.generate_PKT1_1();
        System.out.println("total time: "+(System.currentTimeMillis()-t0));
    }

    @Test
    public void testWsParameter() throws Exception {
        //subscribe to parameters
        NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6","/REFMDB/SUBSYS1/InvalidParaName");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        wsClient.sendRequest(wsr);

        NamedObjectId invalidId = wsListener.invalidIdentificationList.poll(5, TimeUnit.SECONDS);
        assertNotNull(invalidId);
        assertEquals("/REFMDB/SUBSYS1/InvalidParaName", invalidId.getName());
        //TODO: because there is an invalid parameter, the request is sent back so we have to wait a little; 
        // should fix this - we should have an ack that the thing has been subscribed 
        Thread.sleep(1000);
        //generate some TM packets and monitor realtime reception
        for (int i=0;i <10; i++) packetGenerator.generate_PKT1_1();
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        checkPvals(pdata.getParameterList(), packetGenerator);

        NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        wsr = new WebSocketRequest("parameter", "unsubscribe", subscrList);
        wsClient.sendRequest(wsr);

        //we subscribe again and should get the previous values from the cache
        wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        wsClient.sendRequest(wsr);
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        checkPvals(pdata.getParameterList(), packetGenerator);
    }

    @Test
    public void testWsTime() throws Exception {      
        WebSocketRequest wsr = new WebSocketRequest("time", "subscribe");
        wsClient.sendRequest(wsr);
        TimeInfo ti = wsListener.timeInfoList.poll(2, TimeUnit.SECONDS);
        assertNotNull(ti);
    }

    @Test
    public void testRestParameterGet() throws Exception {
        ////// gets parameters from cache via REST - first attempt with one invalid parameter
        NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6","/REFMDB/SUBSYS1/InvalidParaName");
        BulkGetParameterValueRequest req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(invalidSubscrList.getListList()).build();

        String response = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);
        assertTrue(response.contains("Invalid parameters"));
        assertTrue(response.contains("/REFMDB/SUBSYS1/InvalidParaName"));

        packetGenerator.generate_PKT1_1();
        Thread.sleep(1000);
        /////// gets parameters from cache via REST - second attempt with valid parameters
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getListList()).build();

        response = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);
        BulkGetParameterValueResponse bulkPvals = (fromJson(response, SchemaRest.BulkGetParameterValueResponse.MERGE)).build();
        checkPvals(bulkPvals.getValueList(), packetGenerator);

        /////// gets parameters from via REST - waiting for update - first test the timeout in case no update is coming
        long t0 = System.currentTimeMillis();
        req = BulkGetParameterValueRequest.newBuilder()
                .setFromCache(false)
                .setTimeout(2000).addAllId(validSubscrList.getListList()).build();

        Future<String> responseFuture = httpClient.doAsyncRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);

        LoggerFactory.getLogger(getClass()).error("GET  BACK                      " + responseFuture.get());
        bulkPvals = (fromJson(responseFuture.get(), SchemaRest.BulkGetParameterValueResponse.MERGE)).build();
        long t1 = System.currentTimeMillis();
        assertEquals(2000, t1-t0, 200);
        assertEquals(0, bulkPvals.getValueCount());
        //////// gets parameters from via REST - waiting for update - now with some parameters updated
        packetGenerator.pIntegerPara1_1_6 = 10;
        packetGenerator.pIntegerPara1_1_7 = 5;
        responseFuture = httpClient.doAsyncRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);
        Thread.sleep(1000); //wait to make sure that the data has reached the server

        packetGenerator.generate_PKT1_1();

        bulkPvals = (fromJson(responseFuture.get(), SchemaRest.BulkGetParameterValueResponse.MERGE)).build();

        checkPvals(bulkPvals.getValueList(), packetGenerator);
    }

    @Test
    public void testRestParameterSetInvalidParam() throws Exception {
        BulkSetParameterValueRequest.Builder bulkb = BulkSetParameterValueRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"));
        requestb.setValue(ValueHelper.newValue(3.14));
        bulkb.addRequest(requestb);
        
        String resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST, toJson(bulkb.build(), SchemaRest.BulkSetParameterValueRequest.WRITE), currentUser);
        assertTrue(resp.contains("Cannot find a local(software)"));
    }

    @Test
    public void testRestParameterSetInvalidType() throws Exception {
        BulkSetParameterValueRequest.Builder bulkb = BulkSetParameterValueRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"));
        requestb.setValue(ValueHelper.newValue("blablab"));
        bulkb.addRequest(requestb);
        
        String resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST, toJson(bulkb.build(), SchemaRest.BulkSetParameterValueRequest.WRITE), currentUser);
        assertTrue(resp.contains("Cannot assign"));
    }

    @Test
    public void testRestParameterSet() throws Exception {
        BulkSetParameterValueRequest.Builder bulkb = BulkSetParameterValueRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"));
        requestb.setValue(ValueHelper.newValue(5));
        bulkb.addRequest(requestb);
        
        HttpClient httpClient = new HttpClient();
        String resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST, toJson(bulkb.build(), SchemaRest.BulkSetParameterValueRequest.WRITE), currentUser);
        assertNotNull(resp);

        Thread.sleep(1000); //the software parameter manager sets the parameter in another thread so it might not be immediately avaialble
        httpClient = new HttpClient();
        resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara1", HttpMethod.GET, null, currentUser);
        ParameterValue pv = (fromJson(resp, SchemaPvalue.ParameterValue.MERGE)).build();
        assertEquals(requestb.getValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSet2() throws Exception {
        //test simple set just for the value	
        Value v = ValueHelper.newValue(3.14);
        String resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara2", HttpMethod.POST, toJson(v, SchemaYamcs.Value.WRITE), currentUser);
        assertNotNull(resp);

        Thread.sleep(1000); //the software parameter manager sets the parameter in another thread so it might not be immediately avaialble
        resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara2", HttpMethod.GET, null, currentUser);
        ParameterValue pv = (fromJson(resp, SchemaPvalue.ParameterValue.MERGE)).build();
        assertEquals(v, pv.getEngValue());
    }


    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        //first subscribe to command history
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        String resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq, SchemaRest.IssueCommandRequest.WRITE);
        assertTrue(resp.contains("binary"));

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", cmdid.getCommandName());
        assertEquals(5, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
    }
    
    /*@Test
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
        String resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC1", HttpMethod.POST, cmdreq, SchemaRest.IssueCommandRequest.WRITE);
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
    }

    @Test
    public void testSendCommandSucceedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        IssueCommandRequest cmdreq = getCommand(6, "p1", "2");
        String resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC2", HttpMethod.POST, cmdreq, SchemaRest.IssueCommandRequest.WRITE);
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
        httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/AllowCriticalTC2", HttpMethod.POST, toJson(v, SchemaYamcs.Value.WRITE), currentUser);
        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(cmdhist);

        assertEquals(1, cmdhist.getAttrCount());

        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.TransmissionContraints_KEY, cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
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



    @Test
    public void testRetrieveIndex() throws Exception {

    }

    /*
    private ValidateCommandRequest getValidateCommand(String cmdName, int seq, String... args) {
        NamedObjectId cmdId = NamedObjectId.newBuilder().setName(cmdName).build();

        CommandType.Builder cmdb = CommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq);
        for(int i =0 ;i<args.length; i+=2) {
            cmdb.addArguments(ArgumentAssignmentType.newBuilder().setName(args[i]).setValue(args[i+1]).build());
        }

        return ValidateCommandRequest.newBuilder().addCommand(cmdb.build()).build();
    }
    */

    private ProcessorInfo getProcessorInfo() throws InterruptedException {
        WebSocketRequest wsr = new WebSocketRequest("management", ManagementResource.OP_getProcessorInfo);
        wsClient.sendRequest(wsr);
        ProcessorInfo pinfo = wsListener.processorInfoList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pinfo);
        return pinfo;
    }

   
    
    // Keeping it D-R-Y. Could be refactored into httpClient to make writing short tests easier
    private <T extends MessageLite> String doRealtimeRequest(String path, HttpMethod method, T msg, Schema<T> schema) throws Exception {
        String json = toJson(msg, schema);
        return httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime" + path, method, json, currentUser);
    }

    private void checkPvals(List<ParameterValue> pvals, RefMdbPacketGenerator packetProvider) {
        assertNotNull(pvals);

        assertEquals(2, pvals.size());
        
        org.yamcs.protobuf.Pvalue.ParameterValue p1 = pvals.get(0);
        org.yamcs.protobuf.Pvalue.ParameterValue p2 = pvals.get(1);
        if(!"/REFMDB/SUBSYS1/IntegerPara1_1_6".equals(p1.getId().getName())) {
            //swap the parameters because they may be sent in the reverse order from the cache.
            //TODO: shouldn't the paramerter cache keep track of the correct order
            org.yamcs.protobuf.Pvalue.ParameterValue ptmp = p1;
            p1 = p2;
            p2 = ptmp;
        }
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", p1.getId().getName());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_7", p2.getId().getName());

        Value p1raw = p1.getRawValue();
        assertNotNull(p1raw);
        assertEquals(Type.UINT32 , p1raw.getType());
        assertEquals(packetProvider.pIntegerPara1_1_6 , p1raw.getUint32Value());

        Value p1eng = p1.getEngValue();
        assertEquals(Type.UINT32 , p1eng.getType());
        assertEquals(packetProvider.pIntegerPara1_1_6 , p1eng.getUint32Value());

        Value p2raw = p2.getRawValue();
        assertNotNull(p2raw);
        assertEquals(Type.UINT32 , p2raw.getType());
        assertEquals(packetProvider.pIntegerPara1_1_7 , p2raw.getUint32Value());

        Value p2eng = p2.getEngValue();
        assertEquals(Type.UINT32 , p2eng.getType());
        assertEquals(packetProvider.pIntegerPara1_1_7 , p2eng.getUint32Value());
    }
  
   
}
