package org.yamcs;

import static org.junit.Assert.*;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.ws.ParameterSubscribeRequest;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallbackListener;
import org.yamcs.api.ws.YamcsConnectionProperties;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Rest.RestGetParameterRequest;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.HttpClient;
import org.yamcs.yarch.YarchTestCase;

import com.google.protobuf.MessageLite;

public class IntegrationTest extends YarchTestCase {
   
    @BeforeClass
    public static void beforeClass() throws Exception {
	setupYamcs();
	
	boolean debug = false;
	if(debug) {
	    Logger.getLogger("org.yamcs").setLevel(Level.ALL);
	}
    }
    
    private static void setupYamcs() throws Exception {
	System.out.println("in setupYamcs");
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


    @Test
    public void test() throws Exception {
	PacketProvider packetProvider = PacketProvider.instance;
	
	assertNotNull(packetProvider);

	
	//subscribe to parameters
	YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190, "IntegrationTest");
	MyWsListener wsListener = new MyWsListener();
	WebSocketClient wsClient = new WebSocketClient(ycp, wsListener);
	wsClient.connect();
	assertTrue(wsListener.onConnect.tryAcquire(5, TimeUnit.SECONDS));
	NamedObjectList invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6","/REFMDB/SUBSYS1/InvalidParaName"); 
	 
	ParameterSubscribeRequest prs = new ParameterSubscribeRequest(invalidSubscrList);
	wsClient.sendRequest(prs);
	
	NamedObjectId invalidId = wsListener.invalidIdentificationList.poll(5, TimeUnit.SECONDS);
	assertNotNull(invalidId);
	assertEquals("/REFMDB/SUBSYS1/InvalidParaName", invalidId.getName());
	
	//TODO: because there is an invalid parameter, the request is sent back so we have to wait a little; 
	// should fix this - we should have an ack that the thing has been subscribed 
	Thread.sleep(1000);
	
	//generate some TM packets and monitor realtime reception
	packetProvider.generate_PKT11();
	ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
	
	checkPdata(pdata, packetProvider);
	
	
	
	////// gets parameters from cache via REST - first attempt with one invalid parameter
	
	 HttpClient httpClient = new HttpClient();
	RestGetParameterRequest req = RestGetParameterRequest.newBuilder()
		.setFromCache(true).addAllList(invalidSubscrList.getListList()).build();
	
	String response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
	assertTrue(response.contains("Invalid parameters"));
	assertTrue(response.contains("/REFMDB/SUBSYS1/InvalidParaName"));
	

	/////// gets parameters from cache via REST - second attempt with valid parameters
	NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_6", "/REFMDB/SUBSYS1/IntegerPara11_7");
	httpClient = new HttpClient();
	req = RestGetParameterRequest.newBuilder()
		.setFromCache(true).addAllList(validSubscrList.getListList()).build();
	
	response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
	pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
	checkPdata(pdata, packetProvider);
	
	/////// gets parameters from via REST - waiting for update - first test the timeout in case no update is coming
	httpClient = new HttpClient();
	long t0 = System.currentTimeMillis();
	req = RestGetParameterRequest.newBuilder()
		.setTimeout(2000).addAllList(validSubscrList.getListList()).build();
	Future<String> responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
	
	pdata = (fromJson(responseFuture.get(), SchemaPvalue.ParameterData.MERGE)).build();
	long t1 = System.currentTimeMillis();
	assertEquals( 2000, t1-t0, 200);
	assertEquals(0, pdata.getParameterCount());
	//////// gets parameters from via REST - waiting for update - now with some parameters updated
	httpClient = new HttpClient();
	packetProvider.pIntegerPara11_6 = 10;
	packetProvider.pIntegerPara11_7 = 5;
	responseFuture = httpClient.doAsyncRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.RestGetParameterRequest.WRITE));
	Thread.sleep(1000); //wait to make sure that the data has reached the server
	
	packetProvider.generate_PKT11();
	
	pdata = (fromJson(responseFuture.get(), SchemaPvalue.ParameterData.MERGE)).build();
	
	checkPdata(pdata, packetProvider);
	
	
	
	//perform a replay

	//retrieve data

	//retrieve index

	//send a TC
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
    
    
    @Test
    public void test1() throws Exception {
	
	//gets parameters from cache via REST
	NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6","/REFMDB/SUBSYS1/InvalidParaName"); 
	RestGetParameterRequest req = RestGetParameterRequest.newBuilder()
		.setFromCache(true).addAllList(subscrList.getListList()).build();
	
		
	String body = toJson(req, SchemaRest.RestGetParameterRequest.WRITE);
	System.out.println("body: "+body);
	HttpClient httpClient = new HttpClient();
	String result = httpClient.doRequest("http://localhost", HttpMethod.GET, body);
	System.out.println("result: "+result);
	
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
	LinkedBlockingQueue<NamedObjectId> invalidIdentificationList = new LinkedBlockingQueue<NamedObjectId>();
	LinkedBlockingQueue<ParameterData> parameterDataList = new LinkedBlockingQueue<ParameterData>();
	
	@Override
	public void onConnect() {
	    onConnect.release();

	}

	@Override
	public void onDisconnect() {
	    // TODO Auto-generated method stub

	}

	@Override
	public void onInvalidIdentification(NamedObjectId id) {
	    invalidIdentificationList.add(id);
	}

	@Override
	public void onParameterData(ParameterData pdata) {
	    parameterDataList.add(pdata);
	}

	@Override
	public void onCommandHistoryData(CommandHistoryEntry cmdhistData) {
	    System.out.println("onCommandHistoryData");
	}
    }
    
    
    public static class PacketProvider extends RefMdbPacketGenerator {
	static volatile PacketProvider instance;
	public PacketProvider(String yinstance, String name, String spec) {
	    instance = this;
	}
    }
}
