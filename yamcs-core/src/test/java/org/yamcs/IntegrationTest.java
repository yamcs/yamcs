package org.yamcs;

import static org.junit.Assert.*;
import io.netty.handler.codec.http.HttpMethod;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallbackListener;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.YamcsConnectionProperties;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.management.ManagementService;
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
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.security.Privilege;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.HttpClient;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.ManagementClient;
import org.yamcs.web.websocket.ParameterClient;

import com.google.protobuf.MessageLite;

public class IntegrationTest {
    PacketProvider packetProvider;
    YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190, "IntegrationTest");
    MyWsListener wsListener;
    WebSocketClient wsClient;
    HttpClient httpClient;
    UsernamePasswordToken admin = new UsernamePasswordToken("admin", "rootpassword");;
    UsernamePasswordToken currentUser = null;

    @BeforeClass
    public static void beforeClass() throws Exception {
        setupYamcs();
        boolean debug = false;
        if(debug) {
            Logger.getLogger("org.yamcs").setLevel(Level.ALL);
        }
    }

    private static void setupYamcs() throws Exception {
        File dataDir=new File("/tmp/yamcs-IntegrationTest-data");

        FileUtils.deleteRecursively(dataDir.toPath());

        EventProducerFactory.setMockup(true);
        YConfiguration.setup("IntegrationTest");
        ManagementService.setup(false, false);
        org.yamcs.yarch.management.ManagementService.setup(false);
        YamcsServer.setupHornet();
        YamcsServer.setupYamcsServer();

    }

    @AfterClass
    public static void shutDownYamcs()  throws Exception {
        YamcsServer.shutDown();
        YamcsServer.stopHornet();
    }

    @Before
    public void before() throws InterruptedException {


        if(Privilege.getInstance().isEnabled())
        {
            currentUser = admin;
        }

        packetProvider = PacketProvider.instance;
        assertNotNull(packetProvider);
        wsListener = new MyWsListener();
        if(currentUser != null)
            wsClient = new WebSocketClient(ycp, wsListener, currentUser.getUsername(), currentUser.getPasswordS());
        else
            wsClient = new WebSocketClient(ycp, wsListener, null, null);
        wsClient.setUserAgent("it-junit");
        wsClient.connect();
        assertTrue(wsListener.onConnect.tryAcquire(5, TimeUnit.SECONDS));
        httpClient = new HttpClient();
        packetProvider.setGenerationTime(TimeEncoding.INVALID_INSTANT);

//        ClientInfo cinfo = wsListener.clientInfoList.poll(5, TimeUnit.SECONDS);
//        System.out.println("got cinfo:"+cinfo);
//        assertNotNull(cinfo);
    }

    @After
    public void after() throws InterruptedException {
        wsClient.disconnect();
        assertTrue(wsListener.onDisconnect.tryAcquire(5, TimeUnit.SECONDS));
    }

    @Ignore
    @Test
    public void testWsParameterSubscriPerformance() throws Exception {
        //subscribe to parameters
        long t0 = System.currentTimeMillis();
        NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        wsClient.sendRequest(wsr);

        for (int i=0;i <1000000; i++) packetProvider.generate_PKT11();
        System.out.println("total time: "+(System.currentTimeMillis()-t0));
    }

    @Ignore
    @Test
    public void testWsParameter() throws Exception {
        //subscribe to parameters
        NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6","/REFMDB/SUBSYS1/InvalidParaName");


        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        wsClient.sendRequest(wsr);

        NamedObjectId invalidId = wsListener.invalidIdentificationList.poll(5, TimeUnit.SECONDS);
        assertNotNull(invalidId);
        assertEquals("/REFMDB/SUBSYS1/InvalidParaName", invalidId.getName());
        //TODO: because there is an invalid parameter, the request is sent back so we have to wait a little; 
        // should fix this - we should have an ack that the thing has been subscribed 
        Thread.sleep(1000);
        //generate some TM packets and monitor realtime reception
        for (int i=0;i <1000; i++) packetProvider.generate_PKT11();
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        checkPdata(pdata, packetProvider);

        NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6");
        wsr = new WebSocketRequest("parameter", "unsubscribe", subscrList);
        wsClient.sendRequest(wsr);

        //we subscribe again and should get the previous values from the cache
        wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        wsClient.sendRequest(wsr);
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        checkPdata(pdata, packetProvider);
    }



    @Test
    public void testRestParameterGet() throws Exception {
        ////// gets parameters from cache via REST - first attempt with one invalid parameter
        NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6","/REFMDB/SUBSYS1/InvalidParaName");
        RestGetParameterRequest req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(invalidSubscrList.getListList()).build();

        String response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        assertTrue(response.contains("Invalid parameters"));
        assertTrue(response.contains("/REFMDB/SUBSYS1/InvalidParaName"));

        packetProvider.generate_PKT11();
        Thread.sleep(1000);
        /////// gets parameters from cache via REST - second attempt with valid parameters
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_6", "/REFMDB/SUBSYS1/IntegerPara11_7");
        req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(validSubscrList.getListList()).build();

        response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        ParameterData pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
        checkPdata(pdata, packetProvider);

        /////// gets parameters from via REST - waiting for update - first test the timeout in case no update is coming
        long t0 = System.currentTimeMillis();
        req = RestGetParameterRequest.newBuilder()
                .setTimeout(2000).addAllList(validSubscrList.getListList()).build();
        System.out.println("sending request :" +toJson(req, SchemaRest.RestGetParameterRequest.WRITE));

        Future<String> responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);

        pdata = (fromJson(responseFuture.get(), SchemaPvalue.ParameterData.MERGE)).build();
        long t1 = System.currentTimeMillis();
        assertEquals( 2000, t1-t0, 200);
        assertEquals(0, pdata.getParameterCount());
        //////// gets parameters from via REST - waiting for update - now with some parameters updated
        packetProvider.pIntegerPara11_6 = 10;
        packetProvider.pIntegerPara11_7 = 5;
        responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        Thread.sleep(1000); //wait to make sure that the data has reached the server

        packetProvider.generate_PKT11();

        pdata = (fromJson(responseFuture.get(), SchemaPvalue.ParameterData.MERGE)).build();

        checkPdata(pdata, packetProvider);
    }

    @Test
    public void testRestParameterSetInvalidParam() throws Exception {
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = ParameterValue.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara11_6"))
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
        assertEquals("{}", resp);

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
        assertEquals("{}", resp);

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
        assertEquals("{}", resp);

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
        assertNull(cmdhist);

        Value v = ValueHelper.newValue(true);
        httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/AllowCriticalTC2", HttpMethod.POST, toJson(v, SchemaYamcs.Value.WRITE), currentUser);
        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(cmdhist);

        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
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

        httpClient.doRequest("http://localhost:9190/IntegrationTest/api/management/processor", HttpMethod.POST, toJson(prequest, SchemaYamcsManagement.ProcessorManagementRequest.WRITE), currentUser);

        cinfo = getClientInfo();
        assertEquals("testReplay", cinfo.getProcessorName());

        NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter",ParameterClient.WSR_subscribe, subscrList);
        wsClient.sendRequest(wsr);

        ParameterData pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        ParameterValue p11_6 = pdata.getParameter(0);
        assertEquals("2015-01-01T10:01:00.000", p11_6.getGenerationTimeUTC());

        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        p11_6 = pdata.getParameter(0);
        assertEquals("2015-01-01T10:01:01.000", p11_6.getGenerationTimeUTC());

        //go back to realtime
        prequest = ProcessorManagementRequest.newBuilder().addClientId(cinfo.getId())
                .setOperation(ProcessorManagementRequest.Operation.CONNECT_TO_PROCESSOR).setInstance("IntegrationTest").setName("realtime").build();
        httpClient.doPostRequest("http://localhost:9190/IntegrationTest/api/management/processor", toJson(prequest, SchemaYamcsManagement.ProcessorManagementRequest.WRITE), currentUser);

        cinfo = getClientInfo();
        assertEquals("realtime", cinfo.getProcessorName());
    }

    @Test
    public void testRetrieveDataFromArchive() throws Exception {
        generateData("2015-02-03T10:00:00", 3600);
        NamedObjectId p11_6id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara11_6").build();
        NamedObjectId p13_1id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FixedStringPara13_1").build();

        ParameterReplayRequest prr = ParameterReplayRequest.newBuilder().addNameFilter(p11_6id).addNameFilter(p13_1id).build();
        RestDumpArchiveRequest dumpRequest = RestDumpArchiveRequest.newBuilder().setParameterRequest(prr)
                .setUtcStart("2015-02-03T10:10:00").setUtcStop("2015-02-03T10:10:02").build();
        String response = httpClient.doGetRequest("http://localhost:9190/IntegrationTest/api/archive", toJson(dumpRequest, SchemaRest.RestDumpArchiveRequest.WRITE), currentUser);
        RestDumpArchiveResponse rdar = (fromJson(response, SchemaRest.RestDumpArchiveResponse.MERGE)).build();
        List<ParameterData> plist = rdar.getParameterDataList();
        assertNotNull(plist);
        assertEquals(4, plist.size());
        ParameterValue pv0 = plist.get(0).getParameter(0);
        assertEquals("2015-02-03T10:10:00.000", pv0.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara11_6", pv0.getId().getName());
        ParameterValue pv3 = plist.get(3).getParameter(0);
        assertEquals("2015-02-03T10:10:01.000", pv3.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/FixedStringPara13_1", pv3.getId().getName());
    }

    @Test
    public void testRetrieveIndex() throws Exception {

    }


    @Test
    public void testAuthenticationWebServices() throws Exception {
        UsernamePasswordToken wrongUser = new UsernamePasswordToken("baduser", "wrongpassword");
        currentUser = wrongUser;
        boolean gotException = false;
        try {
            testRetrieveDataFromArchive();
        }catch (Exception e)
        {
            gotException = true;
        }
        assertTrue("replay request should be denied to user", gotException);
    }

    @Test
    public void testPermissionArchive() throws Exception {

        // testuser is allowed to replay integer parameters but no string parameters
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Check that integer parameter replay is ok
        generateData("2015-02-02T10:00:00", 3600);
        NamedObjectId p11_6id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara11_6").build();
        ParameterReplayRequest prr = ParameterReplayRequest.newBuilder().addNameFilter(p11_6id).build();
        RestDumpArchiveRequest dumpRequest = RestDumpArchiveRequest.newBuilder().setParameterRequest(prr)
                .setUtcStart("2015-02-02T10:10:00").setUtcStop("2015-02-02T10:10:02").build();
        String response = httpClient.doGetRequest("http://localhost:9190/IntegrationTest/api/archive", toJson(dumpRequest, SchemaRest.RestDumpArchiveRequest.WRITE), currentUser);
        RestDumpArchiveResponse rdar = (fromJson(response, SchemaRest.RestDumpArchiveResponse.MERGE)).build();
        List<ParameterData> plist = rdar.getParameterDataList();
        assertNotNull(plist);
        assertEquals(2, plist.size());
        ParameterValue pv0 = plist.get(0).getParameter(0);
        assertEquals("2015-02-02T10:10:00.000", pv0.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara11_6", pv0.getId().getName());

        // Check that string parameter replay is denied
        boolean gotException = false;
        try {
            generateData("2015-02-02T10:00:00", 3600);
            NamedObjectId p13_1id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FixedStringPara13_1").build();
            prr = ParameterReplayRequest.newBuilder().addNameFilter(p13_1id).build();
            dumpRequest = RestDumpArchiveRequest.newBuilder().setParameterRequest(prr)
                    .setUtcStart("2015-02-02T10:10:00").setUtcStop("2015-02-02T10:10:02").build();
            response = httpClient.doGetRequest("http://localhost:9190/IntegrationTest/api/archive", toJson(dumpRequest, SchemaRest.RestDumpArchiveRequest.WRITE), currentUser);
            rdar = (fromJson(response, SchemaRest.RestDumpArchiveResponse.MERGE)).build();
            plist = rdar.getParameterDataList();
            if(plist.size() == 0)
            {
                throw  new Exception("should get parameters");
            }
        }
        catch(Exception e)
        {
            gotException = true;
        }
        assertTrue("Permission should be denied for String parameter", gotException);
    }


    @Test
    public void testPermissionSendCommand() throws Exception {
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Command INT_ARG_TC is allowed
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);
        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/INT_ARG_TC", 5, "uint32_arg", "1000");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertEquals("{}", resp);

        // Command FLOAT_ARG_TC is denied
        cmdreq = getCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC", 5, "float_arg", "-15", "double_arg", "0");
        resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertTrue("Should get permission exception message", resp.contains("ForbiddenException"));
    }

    @Test
    public void testPermissionGetParameter() throws Exception {
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Allowed to subscribe to Integer parameter from cache
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_6", "/REFMDB/SUBSYS1/IntegerPara11_7");
        RestGetParameterRequest req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(validSubscrList.getListList()).build();
        String response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        assertTrue("{}", !response.contains("ForbiddenException"));

        // Denied to subscribe to Float parameter from cache
        validSubscrList = getSubscription("/REFMDB/SUBSYS1/FloatPara11_3", "/REFMDB/SUBSYS1/FloatPara11_2");
        req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(validSubscrList.getListList()).build();
        response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE), currentUser);
        assertTrue("Permission should be denied", response.contains("ForbiddenException"));

    }

    
    @Test
    public void testCommandVerification() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/VERIFICATION_TC", 7);
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertEquals("{}", resp);

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/VERIFICATION_TC", cmdid.getCommandName());
        assertEquals(7, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
        packetProvider.generateCmdAck((short)1001, (byte)0, 0);
        
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Execution", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
        
        packetProvider.generateCmdAck((short)1001, (byte)5, 0);
        
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Complete", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
        
        
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.CommandComplete_KEY, cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
    }

    
    private RestValidateCommandRequest getValidateCommand(String cmdName, int seq, String... args) {
        NamedObjectId cmdId = NamedObjectId.newBuilder().setName(cmdName).build();

        RestCommandType.Builder cmdb = RestCommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq);
        for(int i =0 ;i<args.length; i+=2) {
            cmdb.addArguments(RestArgumentType.newBuilder().setName(args[i]).setValue(args[i+1]).build());
        }

        return RestValidateCommandRequest.newBuilder().addCommands(cmdb.build()).build();

    }

    private RestSendCommandRequest getCommand(String cmdName, int seq, String... args) {
        NamedObjectId cmdId = NamedObjectId.newBuilder().setName(cmdName).build();

        RestCommandType.Builder cmdb = RestCommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq);
        for(int i =0 ;i<args.length; i+=2) {
            cmdb.addArguments(RestArgumentType.newBuilder().setName(args[i]).setValue(args[i+1]).build());
        }

        return RestSendCommandRequest.newBuilder().addCommands(cmdb.build()).build();

    }




    private ClientInfo getClientInfo() throws InterruptedException {
        WebSocketRequest wsr = new WebSocketRequest("management", ManagementClient.OP_getClientInfo);
        wsClient.sendRequest(wsr);
        ClientInfo cinfo = wsListener.clientInfoList.poll(5, TimeUnit.SECONDS);
        assertNotNull(cinfo);
        return cinfo;
    }


    private ProcessorInfo getProcessorInfo() throws InterruptedException {
        WebSocketRequest wsr = new WebSocketRequest("management", ManagementClient.OP_getProcessorInfo);
        wsClient.sendRequest(wsr);
        ProcessorInfo pinfo = wsListener.processorInfoList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pinfo);
        return pinfo;
    }

    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
            packetProvider.setGenerationTime(t0+1000*i);
            packetProvider.generate_PKT11();
            packetProvider.generate_PKT13();
        }
    }


    private void checkPdata(ParameterData pdata, RefMdbPacketGenerator packetProvider) {
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        org.yamcs.protobuf.Pvalue.ParameterValue p1 = pdata.getParameter(0);
        org.yamcs.protobuf.Pvalue.ParameterValue p2 = pdata.getParameter(1);

        assertEquals("/REFMDB/SUBSYS1/IntegerPara11_6", p1.getId().getName());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara11_7", p2.getId().getName());

        Value p1raw = p1.getRawValue();
        assertNotNull(p1raw);
        assertEquals(Type.UINT32 , p1raw.getType());
        assertEquals(packetProvider.pIntegerPara11_6 , p1raw.getUint32Value());

        Value p1eng = p1.getEngValue();
        assertEquals(Type.UINT32 , p1eng.getType());
        assertEquals(packetProvider.pIntegerPara11_6 , p1eng.getUint32Value());

        Value p2raw = p2.getRawValue();
        assertNotNull(p2raw);
        assertEquals(Type.UINT32 , p2raw.getType());
        assertEquals(packetProvider.pIntegerPara11_7 , p2raw.getUint32Value());

        Value p2eng = p2.getEngValue();
        assertEquals(Type.UINT32 , p2eng.getType());
        assertEquals(packetProvider.pIntegerPara11_7 , p2eng.getUint32Value());
    }


    private <T extends MessageLite> String toJson(T msg, Schema<T> schema) throws IOException {
        StringWriter writer = new StringWriter();
        JsonIOUtil.writeTo(writer, msg, schema, false);
        return writer.toString();
    }

    private <T extends MessageLite.Builder> T fromJson(String jsonstr, Schema<T> schema) throws IOException {
        StringReader reader = new StringReader(jsonstr);
        T msg = schema.newMessage();
        JsonIOUtil.mergeFrom(reader, msg, schema, false);
        return msg;
    }


    private NamedObjectList getSubscription(String... pfqname) {
        NamedObjectList.Builder b = NamedObjectList.newBuilder();
        for(String p: pfqname) {
            b.addList(NamedObjectId.newBuilder().setName(p).build());
        }
        return b.build();
    }


    class MyWsListener implements WebSocketClientCallbackListener {
        Semaphore onConnect = new Semaphore(0);
        Semaphore onDisconnect = new Semaphore(0);

        LinkedBlockingQueue<NamedObjectId> invalidIdentificationList = new LinkedBlockingQueue<NamedObjectId>();
        LinkedBlockingQueue<ParameterData> parameterDataList = new LinkedBlockingQueue<ParameterData>();
        LinkedBlockingQueue<CommandHistoryEntry> cmdHistoryDataList = new LinkedBlockingQueue<CommandHistoryEntry>();
        LinkedBlockingQueue<ClientInfo> clientInfoList = new LinkedBlockingQueue<ClientInfo>();
        LinkedBlockingQueue<ProcessorInfo> processorInfoList = new LinkedBlockingQueue<ProcessorInfo>();


        int count =0;
        @Override
        public void onConnect() {
            onConnect.release();

        }

        @Override
        public void onDisconnect() {
            onDisconnect.release();
        }

        @Override
        public void onInvalidIdentification(NamedObjectId id) {
            invalidIdentificationList.add(id);
        }

        @Override
        public void onParameterData(ParameterData pdata) {
            count++;
            if((count %1000) ==0 ){
                System.out.println("received pdata "+count);
            }

            parameterDataList.add(pdata);
        }

        @Override
        public void onCommandHistoryData(CommandHistoryEntry cmdhistData) {
            cmdHistoryDataList.add(cmdhistData);
        }

        @Override
        public void onClientInfoData(ClientInfo clientInfo) {
            clientInfoList.add(clientInfo);
        }

        @Override
        public void onProcessorInfoData(ProcessorInfo processorInfo) {
            processorInfoList.add(processorInfo);
        }
    }


    public static class PacketProvider extends RefMdbPacketGenerator {
        static volatile PacketProvider instance;
        public PacketProvider(String yinstance, String name, String spec) {
            instance = this;
        }
    }
}
