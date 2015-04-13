package org.yamcs;

import static org.junit.Assert.*;

import java.io.File;
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
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.YarchTestCase;

public class IntegrationTest extends YarchTestCase {

    @BeforeClass
    public static void setupYamcs() throws Exception {
	File dataDir=new File("/tmp/yamcs-IntegrationTest-data");               

	FileUtils.deleteRecursively(dataDir.toPath());

	EventProducerFactory.setMockup(true);
	YConfiguration.setup("IntegrationTest");
	ManagementService.setup(false, false);
	org.yamcs.yarch.management.ManagementService.setup(false);
	YamcsServer.setupHornet();
	YamcsServer.setupYamcsServer();
	boolean debug = false;
	if(debug) {
	    Logger.getLogger("org.yamcs").setLevel(Level.ALL);
	}
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
	NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara11_7", "/REFMDB/SUBSYS1/IntegerPara11_6","/REFMDB/SUBSYS1/InvalidParaName"); 
	 
	ParameterSubscribeRequest prs = new ParameterSubscribeRequest(subscrList);
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
	
	

	
	//perform a replay

	//retrieve data

	//retrieve index

	//send a TC
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
