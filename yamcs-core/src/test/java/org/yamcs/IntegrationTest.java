package org.yamcs;

import static org.junit.Assert.*;
import io.netty.handler.codec.http.HttpMethod;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
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
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.RestArgumentType;
import org.yamcs.protobuf.Rest.RestCommandType;
import org.yamcs.protobuf.Rest.RestGetParameterRequest;
import org.yamcs.protobuf.Rest.RestSendCommandRequest;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.HttpClient;
import org.yamcs.yarch.YarchTestCase;

import com.google.protobuf.MessageLite;

public class IntegrationTest extends YarchTestCase {
    PacketProvider packetProvider;
    YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190, "IntegrationTest");
    MyWsListener wsListener;
    WebSocketClient wsClient;
    HttpClient httpClient;

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
        packetProvider = PacketProvider.instance;
        assertNotNull(packetProvider);
        wsListener = new MyWsListener();
        wsClient = new WebSocketClient(ycp, wsListener);
        wsClient.connect();
        assertTrue(wsListener.onConnect.tryAcquire(5, TimeUnit.SECONDS));
        httpClient = new HttpClient();
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
        System.out.println("pdata: "+pdata);
        checkPdata(pdata, packetProvider);
    }

    
    
    @Test
    public void testRestParameterGet() throws Exception {	
        ////// gets parameters from cache via REST - first attempt with one invalid parameter
        NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6","/REFMDB/SUBSYS1/InvalidParaName"); 
        RestGetParameterRequest req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(invalidSubscrList.getListList()).build();

        String response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
        assertTrue(response.contains("Invalid parameters"));
        assertTrue(response.contains("/REFMDB/SUBSYS1/InvalidParaName"));

        packetProvider.generate_PKT11();
        Thread.sleep(1000);
        /////// gets parameters from cache via REST - second attempt with valid parameters
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_6", "/REFMDB/SUBSYS1/IntegerPara11_7");
        req = RestGetParameterRequest.newBuilder().setFromCache(true).addAllList(validSubscrList.getListList()).build();

        response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
        ParameterData pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
        checkPdata(pdata, packetProvider);

        /////// gets parameters from via REST - waiting for update - first test the timeout in case no update is coming
        long t0 = System.currentTimeMillis();
        req = RestGetParameterRequest.newBuilder()
                .setTimeout(2000).addAllList(validSubscrList.getListList()).build();
        System.out.println("sending request :" +toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
        
        Future<String> responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));

        pdata = (fromJson(responseFuture.get(), SchemaPvalue.ParameterData.MERGE)).build();
        long t1 = System.currentTimeMillis();
        assertEquals( 2000, t1-t0, 200);
        assertEquals(0, pdata.getParameterCount());
        //////// gets parameters from via REST - waiting for update - now with some parameters updated
        packetProvider.pIntegerPara11_6 = 10;
        packetProvider.pIntegerPara11_7 = 5;
        responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
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
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_set", HttpMethod.POST, toJson(pdata, SchemaPvalue.ParameterData.WRITE));
        assertTrue(resp.contains("Cannot find a local(software)"));		
    }

    @Test
    public void testRestParameterSetInvalidType() throws Exception {
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = ParameterValue.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setEngValue(ValueHelper.newValue("blablab")).build();
        ParameterData pdata = ParameterData.newBuilder().addParameter(pv1).build();
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_set", HttpMethod.POST, toJson(pdata, SchemaPvalue.ParameterData.WRITE));
        assertTrue(resp.contains("Cannot assign"));		
    }

    @Test
    public void testRestParameterSet() throws Exception {
        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = ParameterValue.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setEngValue(ValueHelper.newValue(5)).build();
        ParameterData pdata = ParameterData.newBuilder().addParameter(pv1).build();
        HttpClient httpClient = new HttpClient();
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_set", HttpMethod.POST, toJson(pdata, SchemaPvalue.ParameterData.WRITE));

        Thread.sleep(1000); //the software parameter manager sets the parameter in another thread so it might not be immediately avaialble
        httpClient = new HttpClient();
        resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/LocalPara1", HttpMethod.GET, null);
        ParameterValue pv = (fromJson(resp, SchemaPvalue.ParameterValue.MERGE)).build();
        assertEquals(pv1.getEngValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSet2() throws Exception {
        //test simple set just for the value	
        Value v = ValueHelper.newValue(3.14);
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/LocalPara2", HttpMethod.POST, toJson(v, SchemaYamcs.Value.WRITE));

        Thread.sleep(1000); //the software parameter manager sets the parameter in another thread so it might not be immediately avaialble
        resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/LocalPara2", HttpMethod.GET, null);
        ParameterValue pv = (fromJson(resp, SchemaPvalue.ParameterValue.MERGE)).build();
        assertEquals(v, pv.getEngValue());
    }


    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        //first subscribe to command history
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", 5, "uint32_arg", "1000");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE));
        assertEquals("{}", resp);

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", cmdid.getCommandName());
        assertEquals(5, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
    }

    @Test
    public void testSendCommandFailedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/CRITICAL_TC1", 6, "p1", "2");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE));
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
        assertEquals(CommandHistory.TransmissionContraints_KEY, cha.getName());
        assertEquals("NOK", cha.getValue().getStringValue());
        
        cmdhist = wsListener.cmdHistoryDataList.poll(1, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistory.CommandFailed_KEY, cha.getName());
        assertEquals("Transmission constraints check failed", cha.getValue().getStringValue());
    }

    @Test
    public void testSendCommandSucceedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/CRITICAL_TC2", 6, "p1", "2");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE));
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
        httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/REFMDB/SUBSYS1/AllowCriticalTC2", HttpMethod.POST, toJson(v, SchemaYamcs.Value.WRITE));
        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        
        assertEquals(1, cmdhist.getAttrCount());
        
        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(CommandHistory.TransmissionContraints_KEY, cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
    }


    @Test
    public void testReplay() throws Exception {
    }

    @Test
    public void testRetrieveDataFromArchive() throws Exception {
    }

    @Test
    public void testRetrieveIndex() throws Exception {
    }


    private RestSendCommandRequest getCommand(String cmdName, int seq, String... args) {
        NamedObjectId cmdId = NamedObjectId.newBuilder().setName(cmdName).build();

        RestCommandType.Builder cmdb = RestCommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq);
        for(int i =0 ;i<args.length; i+=2) {
            cmdb.addArguments(RestArgumentType.newBuilder().setName(args[i]).setValue(args[i+1]).build());
        }

        return RestSendCommandRequest.newBuilder().addCommands(cmdb.build()).build();

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
    }


    public static class PacketProvider extends RefMdbPacketGenerator {
        static volatile PacketProvider instance;
        public PacketProvider(String yinstance, String name, String spec) {
            instance = this;
        }
    }
}
