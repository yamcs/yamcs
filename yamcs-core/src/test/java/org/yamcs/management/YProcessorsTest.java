package org.yamcs.management;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.TmPacketProvider;
import org.yamcs.TmProcessor;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorClient;
import org.yamcs.YProcessorException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.ui.YProcessorControlClient;
import org.yamcs.ui.YProcessorListener;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.Statistics;

import com.google.common.util.concurrent.AbstractService;

import static org.junit.Assert.*;


public class YProcessorsTest {
   static EmbeddedHornetQ hornetServer;
    @BeforeClass
    public static void setupHornetAndManagement() throws Exception {
        YConfiguration.setup("YProcessorsTest");
        hornetServer=YamcsServer.setupHornet();
        ManagementService.setup(true,true);
    }
    
    @AfterClass
    public static void afterClass() throws Exception {
        ManagementService.getInstance().shutdown();
	YamcsServer.stopHornet();
    }
    
    @Test
    public void empty() {
	
    }
    
    @Test
    public void createYProcessorWithoutClient() throws Exception {
        YamcsConnector yconnector=new YamcsConnector();
        YProcessorControlClient ccc=new YProcessorControlClient(yconnector);
        ccc.setYProcessorListener(new MyListener("YProcessorsTest"));
        yconnector.connect(YamcsConnectData.parse("yamcs:///")).get(5,TimeUnit.SECONDS);

        try {

            Yamcs.ReplayRequest rr = Yamcs.ReplayRequest.newBuilder().build();
            ccc.createProcessor("yproctest0", "test1", "dummy", rr, false, new int[]{10,14});
            assertTrue("YamcsException was expected", false);
        } catch(YamcsException e) {
            assertEquals("createYProcessor invoked with a list full of invalid client ids", e.getMessage());
        }
        ccc.close();
        yconnector.disconnect();
    }

    @Test
    public void createAndSwitchYProc() throws Exception {
        YamcsConnector yconnector=new YamcsConnector();
        YProcessorControlClient ccc=new YProcessorControlClient(yconnector);
        MyListener ml=new MyListener("yproctest1");
        ccc.setYProcessorListener(ml);
        Future<String> f=yconnector.connect(YamcsConnectData.parse("yamcs:///"));
        f.get(5, TimeUnit.SECONDS);

        Yamcs.ReplayRequest rr = Yamcs.ReplayRequest.newBuilder().build();
        ccc.createProcessor("yproctest1", "yproc1", "dummy", rr, true, new int[0]);
        
        MyYProcClient client=new MyYProcClient();
        YProcessor yp=YProcessor.getInstance("yproctest1", "yproc1");
        assertNotNull(yp);
        
        yp.connect(client);
        client.yproc=yp;
        
        ManagementService.getInstance().registerClient("yproctest1", "yproc1", client);
        
        ccc.createProcessor("yproctest1", "yproc2", "dummy", rr, false, new int[]{1});
        
        Thread.sleep(3000); //to make sure that this event will not overwrite the previous yproctest1,yproc1 one 
        ccc.connectToYProcessor("yproctest1", "yproc1", new int[]{1}); //this one should trigger the closing of non permanent yproc2 because no more client connected
        yp.disconnect(client);
        ManagementService.getInstance().unregisterClient(1);
        yp.quit();
        
        Thread.sleep(3000);//to allow for events to come
        ccc.close();
        yconnector.disconnect();
        
        assertEquals(2, ml.yprocUpdated.size());
        ProcessorInfo ci=ml.yprocUpdated.get("yproc1");
        assertEquals("yproctest1",ci.getInstance());
        assertEquals("yproc1",ci.getName());
        assertEquals("dummy",ci.getType());
        assertEquals(ServiceState.RUNNING, ci.getState());
        
        ci=ml.yprocUpdated.get("yproc2");
        assertEquals("yproctest1",ci.getInstance());
        assertEquals("yproc2",ci.getName());
        assertEquals("dummy",ci.getType());
        assertEquals("",ci.getSpec());
        assertEquals(ServiceState.RUNNING, ci.getState());
        
        
        assertEquals(2, ml.yprocClosedList.size());
        ci=ml.yprocClosedList.get(0);
        assertEquals("yproctest1",ci.getInstance());
        assertEquals("yproc2",ci.getName());
        
        ci=ml.yprocClosedList.get(1);
        assertEquals("yproctest1",ci.getInstance());
        assertEquals("yproc1",ci.getName());
        
        
        assertEquals(3, ml.clientUpdatedList.size());
        
        ClientInfo cli=ml.clientUpdatedList.get(0);
        assertEquals("yproctest1",cli.getInstance());
        assertEquals("yproc1",cli.getProcessorName());
        assertEquals(1,cli.getId());
        assertEquals("random-test-user",cli.getUsername());
        assertEquals("random-app-name",cli.getApplicationName());
        
        cli=ml.clientUpdatedList.get(1);
        assertEquals("yproctest1",cli.getInstance());
        assertEquals("yproc2",cli.getProcessorName());
        assertEquals(1,cli.getId());
        
        cli=ml.clientUpdatedList.get(2);
        assertEquals("yproctest1",cli.getInstance());
        assertEquals("yproc1",cli.getProcessorName());
        assertEquals(1,cli.getId());
        
        assertEquals(1,ml.clientDisconnectedList.size());
        cli=ml.clientDisconnectedList.get(0);
        assertEquals("yproctest1",cli.getInstance());
        assertEquals("yproc1",cli.getProcessorName());
        assertEquals(1,cli.getId());
       
    }
    
    static class MyListener implements YProcessorListener {
        Map<String, ProcessorInfo> yprocUpdated=Collections.synchronizedMap(new HashMap<String, ProcessorInfo>());
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
        public void processorUpdated(ProcessorInfo ci) {
            if(instance.equals(ci.getInstance())) {
                yprocUpdated.put(ci.getName(), ci);
            }
        }

        @Override
        public void yProcessorClosed(ProcessorInfo ci) {
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

    static class MyYProcClient implements YProcessorClient {
        YProcessor yproc;

        @Override
        public void switchYProcessor(YProcessor c, AuthenticationToken authToken) throws YProcessorException {
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