package org.yamcs.management;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.TmPacketProvider;
import org.yamcs.TmProcessor;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorClient;
import org.yamcs.ProcessorException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.ui.ProcessorControlClient;
import org.yamcs.ui.ProcessorListener;
import org.yamcs.ui.YamcsConnector;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.Statistics;

import com.google.common.util.concurrent.AbstractService;

import static org.junit.Assert.*;


public class ProcessorsTest {
   static EmbeddedActiveMQ artemisServer;
    @BeforeClass
    public static void setupHornetAndManagement() throws Exception {
        ManagementService.setup(false);
        YConfiguration.setup("ProcessorsTest");
        YamcsServer.setupYamcsServer();
        //Logger.getLogger("org.yamcs").setLevel(Level.ALL);
    }
    
    @AfterClass
    public static void afterClass() throws Exception {
        ManagementService.getInstance().shutdown();
	YamcsServer.shutDown();
    }
    
    @Test
    public void empty() {
	
    }
    
    @Test
    public void createProcessorWithoutClient() throws Exception {
        YamcsConnector yconnector = new YamcsConnector("ProcessorTest");
        ProcessorControlClient ccc = new ProcessorControlClient(yconnector);
        ccc.setYProcessorListener(new MyListener("YProcessorsTest"));
        yconnector.connect(YamcsConnectionProperties.parse("http://localhost:28090/")).get(5,TimeUnit.SECONDS);

        try {

            Yamcs.ReplayRequest rr = Yamcs.ReplayRequest.newBuilder().setStart(1000).build();
            ccc.createProcessor("yproctest0", "test1", "dummy", rr, false, new int[]{10,14}).get();
            assertTrue("YamcsException was expected", false);
        } catch(ExecutionException e) {
            Throwable cause = e.getCause();
            assertEquals("BadRequestException : createProcessor invoked with a list full of invalid client ids", cause.getMessage());
        }
        yconnector.disconnect();
    }

    @Test
    public void createAndSwitchProcessor() throws Exception {
        YamcsConnector yconnector = new YamcsConnector("ProcessorTest-randname1");
        ProcessorControlClient client1 = new ProcessorControlClient(yconnector);
        MyListener ml=new MyListener("yproctest1");
        client1.setYProcessorListener(ml);
        Future<YamcsConnectionProperties> f = yconnector.connect(YamcsConnectionProperties.parse("http://localhost:28090/yproctest1"));
        f.get(5, TimeUnit.SECONDS);

        Thread.sleep(3000);
        Yamcs.ReplayRequest rr = Yamcs.ReplayRequest.newBuilder()
                .setPacketRequest(PacketReplayRequest.newBuilder().addNameFilter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/PKT1").build()).build())
                .setStart(1000).build();
        client1.createProcessor("yproctest1", "yproc1", "dummy", rr, true, new int[]{}).get();
        
        MyYProcClient client = new MyYProcClient();
        YProcessor yproc1 = YProcessor.getInstance("yproctest1", "yproc1");
        assertNotNull(yproc1);
        
        yproc1.connect(client);
        client.yproc = yproc1;
        
        
        int myClientId = ManagementService.getInstance().registerClient("yproctest1", "yproc1", client);
        
        assertNotNull(ManagementService.getInstance().getClientInfo(myClientId));
        
        client1.createProcessor("yproctest1", "yproc2", "dummy", rr, false, new int[]{myClientId}).get();
        
        
        assertNotNull(ManagementService.getInstance().getClientInfo(myClientId));
        
        
        //this one should trigger the closing of non permanent yproc2 because no more client connected
        CompletableFuture<Void> f1= client1.connectToProcessor("yproctest1", "yproc1", new int[]{myClientId}); 
        f1.get();
        
        yproc1.disconnect(client);
        ManagementService.getInstance().unregisterClient(myClientId);
        
        yproc1.quit();
        assertNull(ManagementService.getInstance().getClientInfo(myClientId));
       
        yconnector.disconnect();
      
        Thread.sleep(3000);//to allow for events to come
        
      /*  for(ProcessorInfo pi: ml.yprocUpdated) {
            System.out.println("\t"+pi.getInstance()+"/"+pi.getName()+" state: "+pi.getState()+" replayState: "+pi.getReplayState());
        }*/
        
        List<ProcessorInfo> l = ml.yprocUpdated.get("realtime");
        assertEquals(1, l.size());
        assertPEquals("realtime", ServiceState.RUNNING, ReplayState.INITIALIZATION, l.get(0));
        
        l = ml.yprocUpdated.get("yproc1");
        assertEquals(4, l.size());
        assertPEquals("yproc1", ServiceState.NEW, ReplayState.INITIALIZATION, l.get(0));        
        assertPEquals("yproc1", ServiceState.RUNNING, ReplayState.RUNNING, l.get(1));
        assertPEquals("yproc1", ServiceState.RUNNING, ReplayState.STOPPED, l.get(2));
        assertPEquals("yproc1", ServiceState.STOPPING, ReplayState.STOPPED, l.get(3));
        
        l = ml.yprocUpdated.get("yproc2");
        assertEquals(4, l.size());
        assertPEquals("yproc2", ServiceState.NEW, ReplayState.INITIALIZATION, l.get(0));
        assertPEquals("yproc2", ServiceState.RUNNING, ReplayState.RUNNING, l.get(1));
        assertPEquals("yproc2", ServiceState.RUNNING, ReplayState.STOPPED, l.get(2));
        assertPEquals("yproc2", ServiceState.STOPPING, ReplayState.STOPPED, l.get(3));
        
        
        
        assertEquals(4, ml.clientUpdatedList.size());
        //first one is from the ProcessorControlClient    
        assertCEquals("yproctest1", "realtime",myClientId-1, "admin", "ProcessorTest-randname1", ml.clientUpdatedList.get(0));
        
        assertCEquals("yproctest1", "yproc1",myClientId, "random-test-user", "random-app-name", ml.clientUpdatedList.get(1));
        assertCEquals("yproctest1", "yproc2",myClientId, "random-test-user", "random-app-name", ml.clientUpdatedList.get(2));
        assertCEquals("yproctest1", "yproc1",myClientId, "random-test-user", "random-app-name", ml.clientUpdatedList.get(3));
    }
    
    private void assertCEquals(String instance, String procName, int clientId, String username, String appname, ClientInfo clientInfo) {
        assertEquals(instance, clientInfo.getInstance());
        assertEquals(procName, clientInfo.getProcessorName());
        assertEquals(clientId, clientInfo.getId());
        assertEquals(username, clientInfo.getUsername());
        assertEquals(appname, clientInfo.getApplicationName());
    }

    private void assertPEquals(String procName, ServiceState state, ReplayState replayState, ProcessorInfo processorInfo) {
        assertEquals(procName, processorInfo.getName());        
        assertEquals(state, processorInfo.getState());
        assertEquals(replayState, processorInfo.getReplayState());
    }

    static class MyListener implements ProcessorListener {
        Map<String, List<ProcessorInfo>> yprocUpdated = new HashMap<>();
        List<ProcessorInfo> yprocClosedList=Collections.synchronizedList(new ArrayList<ProcessorInfo>());
        List<ClientInfo> clientUpdatedList=Collections.synchronizedList(new ArrayList<ClientInfo>());
        List<ClientInfo> clientDisconnectedList=Collections.synchronizedList(new ArrayList<ClientInfo>());
        String instance;
        
        public MyListener(String instance) {
            this.instance=instance;
        }
        
        
        @Override
        public void log(String text) {
           System.out.println("log: "+text);
            
        }

        @Override
        public void popup(String text) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void processorUpdated(ProcessorInfo pi) {
            if(instance.equals(pi.getInstance())) {
                List<ProcessorInfo> l = yprocUpdated.get(pi.getName());
                if(l==null) {
                    l = new ArrayList<ProcessorInfo>();
                    yprocUpdated.put(pi.getName(), l);
                }
                l.add(pi);
            }
        }

        @Override
        public void processorClosed(ProcessorInfo ci) {
            if(instance.equals(ci.getInstance()))
                yprocClosedList.add(ci);
        }

        @Override
        public void clientUpdated(ClientInfo ci) {
            if(instance.equals(ci.getInstance())) {
                clientUpdatedList.add(ci);
            }
        }

        @Override
        public void clientDisconnected(ClientInfo ci) {
            if(instance.equals(ci.getInstance())) {
                clientDisconnectedList.add(ci);
            }
        }

        @Override
        public void updateStatistics(Statistics s) {
            // TODO Auto-generated method stub
            
        }
    }

    static class MyYProcClient implements ProcessorClient {
        YProcessor yproc;

        @Override
        public void switchProcessor(YProcessor c, AuthenticationToken authToken) throws ProcessorException {
            yproc.disconnect(this);
            c.connect(this);
            yproc=c;
        }

        @Override
        public void yProcessorQuit() {
            // TODO Auto-generated method stub
            
        }

        @Override
        public String getUsername() {
            return "random-test-user";
        }

        @Override
        public String getApplicationName() {
            return "random-app-name";
        }
    }
    
    public static class DummyTmProvider extends AbstractService implements TmPacketProvider {
        private TmProcessor tmProcessor;

        public DummyTmProvider(String instance) {
        }
        
        public DummyTmProvider(String instance, Yamcs.ReplayRequest spec) {
        }

        @Override
        public boolean isArchiveReplay() {
            return false;
        }

        @Override
        public void doStop() {
            tmProcessor.finished();
            notifyStopped();
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        public void init(YProcessor proc, TmProcessor tmProcessor) {
            this.tmProcessor = tmProcessor;
        }
    }
    
}