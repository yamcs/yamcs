package org.yamcs.management;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.TmPacketProvider;
import org.yamcs.TmProcessor;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.client.ClientException;
import org.yamcs.client.ClientException.ExceptionData;
import org.yamcs.client.ProcessorControlClient;
import org.yamcs.client.ProcessorListener;
import org.yamcs.client.YamcsClient;
import org.yamcs.protobuf.ClientInfo;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.ServiceState;
import org.yamcs.protobuf.Statistics;
import org.yamcs.security.User;

import com.google.common.util.concurrent.AbstractService;

public class ProcessorsTest {

    @BeforeClass
    public static void setup() throws Exception {
        YConfiguration.setupTest("ProcessorsTest");
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();
        //to avoid getting warnings in the test console for invalid client id
        Logger.getLogger("org.yamcs").setLevel(Level.SEVERE);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        ManagementService.getInstance().shutdown();
        YamcsServer.getServer().shutDown();
    }

    @Test
    public void createProcessorWithoutClient() throws Exception {
        YamcsClient client = YamcsClient.newBuilder("localhost", 28090)
                .withInitialInstance("yproctest0")
                .build();
        ProcessorControlClient ccc = new ProcessorControlClient(client);
        ccc.setProcessorListener(new MyListener("YProcessorsTest"));
        client.connectAnonymously();

        try {
            ccc.createProcessor("yproctest0", "test1", "dummy", null, false, new int[] { 10, 14 }).get();
            assertTrue("YamcsException was expected", false);
        } catch (ExecutionException e) {
            ExceptionData excData = ((ClientException) e.getCause()).getDetail();
            assertEquals("createProcessor invoked with a list full of invalid client ids", excData.getMessage());
        }
        client.close();
    }

    @Test
    public void createAndSwitchProcessor() throws Exception {
        YamcsClient yamcsClient = YamcsClient.newBuilder("localhost", 28090)
                .withUserAgent("ProcessorTest-randname1")
                .withInitialInstance("yproctest1")
                .build();
        ProcessorControlClient client1 = new ProcessorControlClient(yamcsClient);
        MyListener ml = new MyListener("yproctest1");
        client1.setProcessorListener(ml);
        yamcsClient.connectAnonymously();

        Thread.sleep(3000);

        client1.createProcessor("yproctest1", "yproc1", "dummy", null, true, new int[] {}).get();

        ConnectedClient client = new ConnectedClient(new User("random-test-user", null), "random-app-name",
                "random-address");
        Processor processor1 = YamcsServer.getServer().getProcessor("yproctest1", "yproc1");
        assertNotNull(processor1);

        processor1.connect(client);
        client.setProcessor(processor1);

        ManagementService.getInstance().registerClient(client);

        int clientId = client.getId();
        assertNotNull(ManagementService.getInstance().getClient(clientId));

        client1.createProcessor("yproctest1", "yproc2", "dummy", null, false, new int[] { clientId }).get();

        assertNotNull(ManagementService.getInstance().getClient(clientId));

        // this one should trigger the closing of non permanent yproc2 because no more client connected
        CompletableFuture<Void> f1 = client1.connectToProcessor("yproctest1", "yproc1",
                new int[] { clientId });
        f1.get();

        ManagementService.getInstance().unregisterClient(clientId);

        processor1.quit();
        assertNull(ManagementService.getInstance().getClient(clientId));

        yamcsClient.close();

        Thread.sleep(3000);// to allow for events to come

        /*for (Entry<String, List<ProcessorInfo>> entries : ml.procUpdated.entrySet()) {
            System.out.println(entries.getKey());
            System.out.println("----------------");
            for (ProcessorInfo pi : entries.getValue()) {
                System.out.println("\t" + pi.getInstance() + "/" + pi.getName() + " state: " + pi.getState()
                        + " replayState: " + pi.getReplayState());
            }
        }*/

        List<ProcessorInfo> l = ml.procUpdated.get("realtime");
        assertEquals(1, l.size());
        assertPEquals("realtime", ServiceState.RUNNING, l.get(0));

        l = ml.procUpdated.get("yproc1");
        assertEquals(3, l.size());
        assertPEquals("yproc1", ServiceState.NEW, l.get(0));
        assertPEquals("yproc1", ServiceState.RUNNING, l.get(1));
        assertPEquals("yproc1", ServiceState.STOPPING, l.get(2));

        l = ml.procUpdated.get("yproc2");
        assertEquals(3, l.size());
        assertPEquals("yproc2", ServiceState.NEW, l.get(0));
        assertPEquals("yproc2", ServiceState.RUNNING, l.get(1));
        assertPEquals("yproc2", ServiceState.STOPPING, l.get(2));

        assertEquals(4, ml.clientUpdatedList.size());
        // first one is from the ProcessorControlClient
        assertCEquals("yproctest1", "realtime", clientId - 1, "guest", "ProcessorTest-randname1",
                ml.clientUpdatedList.get(0));

        assertCEquals("yproctest1", "yproc1", clientId, "random-test-user", "random-app-name",
                ml.clientUpdatedList.get(1));
        assertCEquals("yproctest1", "yproc2", clientId, "random-test-user", "random-app-name",
                ml.clientUpdatedList.get(2));
        assertCEquals("yproctest1", "yproc1", clientId, "random-test-user", "random-app-name",
                ml.clientUpdatedList.get(3));

        assertEquals(2, ml.procClosedList.size());

    }

    private void assertCEquals(String instance, String procName, int clientId, String username, String appname,
            ClientInfo clientInfo) {
        assertEquals(instance, clientInfo.getInstance());
        assertEquals(procName, clientInfo.getProcessorName());
        assertEquals(clientId, clientInfo.getId());
        assertEquals(username, clientInfo.getUsername());
        assertEquals(appname, clientInfo.getApplicationName());
    }

    private void assertPEquals(String procName, ServiceState state, ProcessorInfo processorInfo) {
        assertEquals(procName, processorInfo.getName());
        assertEquals(state, processorInfo.getState());
    }

    static class MyListener implements ProcessorListener {
        Map<String, List<ProcessorInfo>> procUpdated = new HashMap<>();
        List<ProcessorInfo> procClosedList = Collections.synchronizedList(new ArrayList<ProcessorInfo>());
        List<ClientInfo> clientUpdatedList = Collections.synchronizedList(new ArrayList<ClientInfo>());
        List<ClientInfo> clientDisconnectedList = Collections.synchronizedList(new ArrayList<ClientInfo>());
        String instance;

        public MyListener(String instance) {
            this.instance = instance;
        }

        @Override
        public void log(String text) {
            // System.out.println("log: " + text);
        }

        @Override
        public void popup(String text) {
        }

        @Override
        public void processorUpdated(ProcessorInfo pi) {
            if (instance.equals(pi.getInstance())) {
                List<ProcessorInfo> l = procUpdated.get(pi.getName());
                if (l == null) {
                    l = new ArrayList<>();
                    procUpdated.put(pi.getName(), l);
                }
                l.add(pi);
            }
        }

        @Override
        public void processorClosed(ProcessorInfo ci) {
            if (instance.equals(ci.getInstance())) {
                procClosedList.add(ci);
            }
        }

        @Override
        public void clientUpdated(ClientInfo ci) {
            if (instance.equals(ci.getInstance())) {
                clientUpdatedList.add(ci);
            }
        }

        @Override
        public void clientDisconnected(ClientInfo ci) {
            if (instance.equals(ci.getInstance())) {
                clientDisconnectedList.add(ci);
            }
        }

        @Override
        public void updateStatistics(Statistics s) {
            // TODO Auto-generated method stub

        }
    }

    public static class DummyTmProvider extends AbstractService implements TmPacketProvider {
        private TmProcessor tmProcessor;

        @Override
        public void init(String instance, YConfiguration config) {
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
        public void init(Processor proc) {
            this.tmProcessor = proc.getTmProcessor();
            proc.setPacketProvider(this);
        }
    }
}
