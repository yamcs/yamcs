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
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.CommandSignificance;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.RestArgumentType;
import org.yamcs.protobuf.Rest.RestCommandType;
import org.yamcs.protobuf.Rest.RestDumpArchiveRequest;
import org.yamcs.protobuf.Rest.RestDumpArchiveResponse;
import org.yamcs.protobuf.Rest.RestGetParameterRequest;
import org.yamcs.protobuf.Rest.RestSendCommandRequest;
import org.yamcs.protobuf.Rest.RestValidateCommandRequest;
import org.yamcs.protobuf.Rest.RestValidateCommandResponse;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.utils.HttpClient;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.ManagementResource;
import org.yamcs.web.websocket.ParameterResource;

import io.netty.handler.codec.http.HttpMethod;


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
        checkPdata(pdata, packetGenerator);

        NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        wsr = new WebSocketRequest("parameter", "unsubscribe", subscrList);
        wsClient.sendRequest(wsr);

        //we subscribe again and should get the previous values from the cache
        wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        wsClient.sendRequest(wsr);
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        checkPdata(pdata, packetGenerator);
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
        RestGetParameterRequest req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(invalidSubscrList.getListList()).build();

        String response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        assertTrue(response.contains("Invalid parameters"));
        assertTrue(response.contains("/REFMDB/SUBSYS1/InvalidParaName"));

        packetGenerator.generate_PKT1_1();
        Thread.sleep(1000);
        /////// gets parameters from cache via REST - second attempt with valid parameters
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(validSubscrList.getListList()).build();

        response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        ParameterData pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
        checkPdata(pdata, packetGenerator);

        /////// gets parameters from via REST - waiting for update - first test the timeout in case no update is coming
        long t0 = System.currentTimeMillis();
        req = RestGetParameterRequest.newBuilder()
                .setTimeout(2000).addAllList(validSubscrList.getListList()).build();

        Future<String> responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);

        pdata = (fromJson(responseFuture.get(), SchemaPvalue.ParameterData.MERGE)).build();
        long t1 = System.currentTimeMillis();
        assertEquals( 2000, t1-t0, 200);
        assertEquals(0, pdata.getParameterCount());
        //////// gets parameters from via REST - waiting for update - now with some parameters updated
        packetGenerator.pIntegerPara1_1_6 = 10;
        packetGenerator.pIntegerPara1_1_7 = 5;
        responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        Thread.sleep(1000); //wait to make sure that the data has reached the server

        packetGenerator.generate_PKT1_1();

        pdata = (fromJson(responseFuture.get(), SchemaPvalue.ParameterData.MERGE)).build();

        checkPdata(pdata, packetGenerator);
    }

    @Test
    public void testRestParameterSetInvalidParam() throws Exception {
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = ParameterValue.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .setEngValue(ValueHelper.newValue(3.14)).build();
        ParameterData pdata = ParameterData.newBuilder().addParameter(pv1).build();
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_set", HttpMethod.POST, toJson(pdata, SchemaPvalue.ParameterData.WRITE), currentUser);
        assertTrue(resp.contains("Cannot find a local(software)"));
    }

    @Test
    public void testRestParameterSetInvalidType() throws Exception {
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = ParameterValue.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setEngValue(ValueHelper.newValue("blablab")).build();
        ParameterData pdata = ParameterData.newBuilder().addParameter(pv1).build();
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_set", HttpMethod.POST, toJson(pdata, SchemaPvalue.ParameterData.WRITE), currentUser);
        assertTrue(resp.contains("Cannot assign"));
    }

    @Test
    public void testRestParameterSet() throws Exception {
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = ParameterValue.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setEngValue(ValueHelper.newValue(5)).build();
        ParameterData pdata = ParameterData.newBuilder().addParameter(pv1).build();
        HttpClient httpClient = new HttpClient();
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_set", HttpMethod.POST, toJson(pdata, SchemaPvalue.ParameterData.WRITE), currentUser);
        assertNotNull(resp);

        Thread.sleep(1000); //the software parameter manager sets the parameter in another thread so it might not be immediately avaialble
        httpClient = new HttpClient();
        resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/LocalPara1", HttpMethod.GET, null, currentUser);
        ParameterValue pv = (fromJson(resp, SchemaPvalue.ParameterValue.MERGE)).build();
        assertEquals(pv1.getEngValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSet2() throws Exception {
        //test simple set just for the value	
        Value v = ValueHelper.newValue(3.14);
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/LocalPara2", HttpMethod.POST, toJson(v, SchemaYamcs.Value.WRITE), currentUser);
        assertNotNull(resp);

        Thread.sleep(1000); //the software parameter manager sets the parameter in another thread so it might not be immediately avaialble
        resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/LocalPara2", HttpMethod.GET, null, currentUser);
        ParameterValue pv = (fromJson(resp, SchemaPvalue.ParameterValue.MERGE)).build();
        assertEquals(v, pv.getEngValue());
    }


    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        //first subscribe to command history
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", 5, "uint32_arg", "1000");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertEquals("", resp);

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", cmdid.getCommandName());
        assertEquals(5, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
    }

    
    @Test
    public void testValidateCommand() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        RestValidateCommandRequest cmdreq = getValidateCommand("/REFMDB/SUBSYS1/CRITICAL_TC1", 10, "p1", "2");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/validator", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestValidateCommandRequest.WRITE), currentUser);
        RestValidateCommandResponse vcr = (fromJson(resp, SchemaRest.RestValidateCommandResponse.MERGE)).build();
        assertEquals(1, vcr.getCommandsSignificanceCount());
        CommandSignificance significance = vcr.getCommandsSignificance(0);
        assertEquals(10, significance.getSequenceNumber());
        assertEquals(CommandSignificance.Level.critical, significance.getConsequenceLevel());
        assertEquals("this is a critical command, pay attention", significance.getReasonForWarning());
       
    }

    @Test
    public void testSendCommandFailedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/CRITICAL_TC1", 6, "p1", "2");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertEquals("", resp);

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

        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/CRITICAL_TC2", 6, "p1", "2");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertEquals("", resp);

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
        httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/AllowCriticalTC2", HttpMethod.POST, toJson(v, SchemaYamcs.Value.WRITE), currentUser);
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
    public void testReplay() throws Exception {
        generateData("2015-01-01T10:00:00", 3600);
        ClientInfo cinfo = getClientInfo();
        //create a parameter reply via REST
        ReplayRequest rr = ReplayRequest.newBuilder().setUtcStart("2015-01-01T10:01:00").setUtcStop("2015-01-01T10:05:00")
                .setPacketRequest(PacketReplayRequest.newBuilder().build()).build();
        ProcessorManagementRequest prequest = ProcessorManagementRequest.newBuilder().addClientId(cinfo.getId())
                .setOperation(ProcessorManagementRequest.Operation.CREATE_PROCESSOR).setInstance("IntegrationTest").setName("testReplay").setType("Archive")
                .setReplaySpec(rr).build();

        httpClient.doRequest("http://localhost:9190/IntegrationTest/api/processor", HttpMethod.POST, toJson(prequest, SchemaYamcsManagement.ProcessorManagementRequest.WRITE), currentUser);

        cinfo = getClientInfo();
        assertEquals("testReplay", cinfo.getProcessorName());

        NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter",ParameterResource.WSR_subscribe, subscrList);
        wsClient.sendRequest(wsr);

        ParameterData pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        ParameterValue p1_1_6 = pdata.getParameter(0);
        assertEquals("2015-01-01T10:01:00.000", p1_1_6.getGenerationTimeUTC());

        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        p1_1_6 = pdata.getParameter(0);
        assertEquals("2015-01-01T10:01:01.000", p1_1_6.getGenerationTimeUTC());

        //go back to realtime
        prequest = ProcessorManagementRequest.newBuilder().addClientId(cinfo.getId())
                .setOperation(ProcessorManagementRequest.Operation.CONNECT_TO_PROCESSOR).setInstance("IntegrationTest").setName("realtime").build();
        httpClient.doPostRequest("http://localhost:9190/IntegrationTest/api/processor", toJson(prequest, SchemaYamcsManagement.ProcessorManagementRequest.WRITE), currentUser);

        cinfo = getClientInfo();
        assertEquals("realtime", cinfo.getProcessorName());
    }

    @Test
    public void testRetrieveDataFromArchive() throws Exception {
        generateData("2015-01-02T10:00:00", 3600);
        NamedObjectId p1_1_6id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build();
        NamedObjectId p1_3_1id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FixedStringPara1_3_1").build();

        ParameterReplayRequest prr = ParameterReplayRequest.newBuilder().addNameFilter(p1_1_6id).addNameFilter(p1_3_1id).build();
        RestDumpArchiveRequest dumpRequest = RestDumpArchiveRequest.newBuilder().setParameterRequest(prr)
                .setUtcStart("2015-01-02T10:10:00").setUtcStop("2015-01-02T10:10:02").build();
        String response = httpClient.doGetRequest("http://localhost:9190/IntegrationTest/api/archive", toJson(dumpRequest, SchemaRest.RestDumpArchiveRequest.WRITE), currentUser);
        RestDumpArchiveResponse rdar = (fromJson(response, SchemaRest.RestDumpArchiveResponse.MERGE)).build();
        List<ParameterData> plist = rdar.getParameterDataList();
        assertNotNull(plist);
        assertEquals(4, plist.size());
        ParameterValue pv0 = plist.get(0).getParameter(0);
        assertEquals("2015-01-02T10:10:00.000", pv0.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", pv0.getId().getName());
        ParameterValue pv3 = plist.get(3).getParameter(0);
        assertEquals("2015-01-02T10:10:01.000", pv3.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/FixedStringPara1_3_1", pv3.getId().getName());
    }

    @Test
    public void testRetrieveIndex() throws Exception {

    }


    private RestValidateCommandRequest getValidateCommand(String cmdName, int seq, String... args) {
        NamedObjectId cmdId = NamedObjectId.newBuilder().setName(cmdName).build();

        RestCommandType.Builder cmdb = RestCommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq);
        for(int i =0 ;i<args.length; i+=2) {
            cmdb.addArguments(RestArgumentType.newBuilder().setName(args[i]).setValue(args[i+1]).build());
        }

        return RestValidateCommandRequest.newBuilder().addCommands(cmdb.build()).build();

    }

  



    private ClientInfo getClientInfo() throws InterruptedException {
        WebSocketRequest wsr = new WebSocketRequest("management", ManagementResource.OP_getClientInfo);
        wsClient.sendRequest(wsr);
        ClientInfo cinfo = wsListener.clientInfoList.poll(5, TimeUnit.SECONDS);
        assertNotNull(cinfo);
        return cinfo;
    }


    private ProcessorInfo getProcessorInfo() throws InterruptedException {
        WebSocketRequest wsr = new WebSocketRequest("management", ManagementResource.OP_getProcessorInfo);
        wsClient.sendRequest(wsr);
        ProcessorInfo pinfo = wsListener.processorInfoList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pinfo);
        return pinfo;
    }

    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
        	packetGenerator.setGenerationTime(t0+1000*i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();
        }
    }


    private void checkPdata(ParameterData pdata, RefMdbPacketGenerator packetProvider) {
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        
        org.yamcs.protobuf.Pvalue.ParameterValue p1 = pdata.getParameter(0);
        org.yamcs.protobuf.Pvalue.ParameterValue p2 = pdata.getParameter(1);
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
  
    private NamedObjectList getSubscription(String... pfqname) {
        NamedObjectList.Builder b = NamedObjectList.newBuilder();
        for(String p: pfqname) {
            b.addList(NamedObjectId.newBuilder().setName(p).build());
        }
        return b.build();
    }
}
