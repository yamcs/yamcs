package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.tctm.TcpTcDataLinkTest;
import org.yamcs.tctm.ccsds.Cop1Monitor.AlertType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueHelper;

public class Cop1TcPacketHandlerTest {

    Cop1TcPacketHandler fop1ph;

    ScheduledThreadPoolExecutor executor;
    static TcFrameFactory tcFrameFactory;
    MyMonitor monitor;
    int vcId = 0;
    TcTransferFrame adf0, adf1, adf2;
    static TcManagedParameters tcParams;
    Semaphore dataAvailable;

    @BeforeAll
    public static void beforeClass() {
        Map<String, Object> m = new HashMap<>();
        m.put("spacecraftId", 6);

        m.put("maxFrameLength", 1000);
        m.put("errorDetection", "NONE");
        Map<String, Object> vc0 = new HashMap<>();
        List<Map<String, Object>> l = new ArrayList<>();
        l.add(vc0);
        m.put("virtualChannels", l);
        vc0.put("vcId", 0);
        vc0.put("service", "PACKET");
        vc0.put("clcwStream", "clcw");

        tcParams = new TcManagedParameters(YConfiguration.wrap(m));
        tcFrameFactory = new TcFrameFactory(tcParams.getVcParams(0));
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(false);

        // org.yamcs.LoggingUtils.enableLogging();
    }

    @BeforeEach
    public void init() {
        executor = new ScheduledThreadPoolExecutor(1);
        monitor = new MyMonitor();

        fop1ph = new Cop1TcPacketHandler("test", "test", tcParams.getVcParams(0), executor);
        fop1ph.addMonitor(monitor);
        fop1ph.setCommandHistoryPublisher(new TcpTcDataLinkTest.MyCmdHistPublisher(new Semaphore(0)));

        dataAvailable = new Semaphore(0);
        fop1ph.setDataAvailableSemaphore(dataAvailable);

        adf0 = tcFrameFactory.makeFrame(0, 100);
        adf1 = tcFrameFactory.makeFrame(0, 101);
        adf2 = tcFrameFactory.makeFrame(0, 102);
    }

    @AfterEach
    public void stop() {
        executor.shutdown();
    }

    @Test
    public void testInitWithVR_BCTimeout_tt0() throws Exception {
        fop1ph.setT1Initial(100);
        fop1ph.setTransmissionLimit(3);
        fop1ph.setTimeoutType(0);

        fop1ph.initiateADWithVR(3).get();
        verifyState(5);

        // the BC should be transmitted 3 times
        List<TcTransferFrame> l = getFrames(3, 1000);
        assertEquals(3, l.size());

        // after which an alert is sent
        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        assertEquals(AlertType.T1, monitor.alerts.get(0));
        // and state changed into 6
        verifyState(6);

        for (TcTransferFrame bcf : l) {
            assertTrue(bcf.isBypass());
            assertTrue(bcf.isCmdControl());
        }
    }

    @Test
    public void testInitWithVR_BCTimeout_tt1() throws Exception {
        fop1ph.setT1Initial(100);
        fop1ph.setTransmissionLimit(3);
        fop1ph.setTimeoutType(1);

        fop1ph.initiateADWithVR(3).get();
        verifyState(5);

        // the BC should be transmitted 3 times
        List<TcTransferFrame> l = getFrames(3, 1000);
        assertEquals(3, l.size());

        // after which an alert is sent
        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        assertEquals(AlertType.T1, monitor.alerts.get(0));
        // and state changed into 6
        verifyState(6);

        for (TcTransferFrame bcf : l) {
            assertTrue(bcf.isBypass());
            assertTrue(bcf.isCmdControl());
        }
    }

    @Test
    public void testInitWithCLCWCheck_Timeout_tt1() throws Exception {
        fop1ph.setT1Initial(100);
        fop1ph.setTransmissionLimit(3);
        fop1ph.setTimeoutType(1);

        fop1ph.initiateAD(true).get();
        verifyState(4);

        // after which an alert is sent
        assertTrue(monitor.suspendSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        // and state changed into 6
        verifyState(6);
        assertEquals(4, monitor.suspendedState);
    }

    @Test
    public void testInitWithCLCWCheck_Timeout_tt0() throws Exception {
        fop1ph.setT1Initial(100);
        fop1ph.setTransmissionLimit(3);
        fop1ph.setTimeoutType(0);

        fop1ph.initiateAD(true).get();
        verifyState(4);

        // after which an alert is sent
        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        assertEquals(AlertType.T1, monitor.alerts.get(0));
        // and state changed into 6
        verifyState(6);
    }

    @Test
    public void testInitWithClwCheck() throws Exception {
        fop1ph.setTimeoutType(0);
        fop1ph.setVs(10);
        fop1ph.initiateAD(true, 100);

        verifyState(4);
        assertTrue(monitor.alertSema.tryAcquire(1, TimeUnit.SECONDS));
        assertEquals(AlertType.T1, monitor.alerts.get(0));
        verifyState(6);

        fop1ph.initiateAD(true);
        verifyState(4);
        fop1ph.onCLCW(getCLCW(false, false, false, 10));

        verifyState(1);
    }

    @Test
    public void testLockoutAndUnlock() throws Exception {
        fop1ph.setVs(10);
        fop1ph.initiateAD(false);
        fop1ph.onCLCW(getCLCW(true, false, false, 10));
        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        assertEquals(AlertType.LOCKOUT, monitor.alerts.get(0));

        fop1ph.initiateADWithUnlock();
        synchWithExecutor();
        assertEquals(5, monitor.state);
        fop1ph.onCLCW(getCLCW(false, false, false, 10));

        verifyState(1);
    }

    @Test
    public void testWaitWithoutRetransmit() throws Exception {
        fop1ph.setVs(10);
        fop1ph.initiateAD(false);
        fop1ph.onCLCW(getCLCW(false, true, false, 10));
        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));

        assertEquals(AlertType.CLCW, monitor.alerts.get(0));
        verifyState(6);

        fop1ph.initiateAD(false).get();
        sendTcInOneFrame(3);

        fop1ph.onCLCW(getCLCW(false, true, false, 10));
        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        assertEquals(AlertType.CLCW, monitor.alerts.get(1));
        verifyState(6);
    }

    @Test
    public void testWithTxLimit1() throws Exception {
        fop1ph.setVs(10);
        fop1ph.setTransmissionLimit(1);
        fop1ph.initiateAD(false).get();

        TcTransferFrame tf = sendTcInOneFrame(3);
        assertEquals(10, tf.getVcFrameSeq());

        fop1ph.onCLCW(getCLCW(false, false, true, 10));

        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        assertEquals(AlertType.LIMIT, monitor.alerts.get(0));
        verifyState(6);

    }

    @Test
    public void testInitWithVR_ADTimeout() throws Exception {
        fop1ph.setT1Initial(100);
        fop1ph.setTransmissionLimit(3);
        fop1ph.setTimeoutType(0);

        fop1ph.initiateADWithVR(3).get();
        TcTransferFrame tf = fop1ph.getFrame();
        assertNotNull(tf);
        assertTrue(tf.isBypass());
        assertTrue(tf.isCmdControl());

        // send the CLCW with the good nR
        fop1ph.onCLCW(getCLCW(false, false, false, 3));

        synchWithExecutor();
        assertEquals(1, monitor.state);

        sendTcInOneFrame(100);
        List<TcTransferFrame> l = getFrames(3, 1000);

        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));

        assertEquals(AlertType.T1, monitor.alerts.get(0));

        verifyState(6);

        assertEquals(2, l.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(3, l.get(i).getVcFrameSeq());
        }

        fop1ph.terminateAD().get();
    }

    @Test
    public void test2frames() throws Exception {
        fop1ph.initiateADWithVR(3).get();
        // send the CLCW with the good nR
        fop1ph.onCLCW(getCLCW(false, false, false, 3));
        assertNotNull(fop1ph.getFrame());

        TcTransferFrame tf0 = sendTcInOneFrame(89);
        fop1ph.onCLCW(getCLCW(false, false, false, 4));

        TcTransferFrame tf1 = sendTcInOneFrame(90);
        fop1ph.onCLCW(getCLCW(false, false, false, 5));

        verifyState(1);
        assertEquals(3, tf0.getVcFrameSeq());
        assertEquals(4, tf1.getVcFrameSeq());

        assertEquals(89, tf0.getCommands().get(0).getCommandId().getSequenceNumber());
        assertEquals(90, tf1.getCommands().get(0).getCommandId().getSequenceNumber());

        fop1ph.terminateAD().get();
        verifyState(6);
    }

    @Test
    public void test2framesAckWithDelay() throws Exception {
        fop1ph.initiateADWithVR(3).get();
        // send the CLCW with the good nR
        fop1ph.onCLCW(getCLCW(false, false, false, 3));
        synchWithExecutor();

        assertNotNull(fop1ph.getFrame());

        TcTransferFrame tf0 = sendTcInOneFrame(89);
        TcTransferFrame tf1 = sendTcInOneFrame(90);

        fop1ph.onCLCW(getCLCW(false, false, false, 4));
        fop1ph.onCLCW(getCLCW(false, false, false, 5));

        verifyState(1);
        assertEquals(3, tf0.getVcFrameSeq());
        assertEquals(4, tf1.getVcFrameSeq());
    }

    @Test
    public void test2frames_OneAckMissing() throws Exception {
        fop1ph.initiateADWithVR(3).get();
        fop1ph.onCLCW(getCLCW(false, false, false, 3));

        assertNotNull(fop1ph.getFrame());

        TcTransferFrame tf0 = sendTcInOneFrame(89);
        TcTransferFrame tf1 = sendTcInOneFrame(90);

        fop1ph.onCLCW(getCLCW(false, false, false, 5));

        verifyState(1);
        assertEquals(3, tf0.getVcFrameSeq());
        assertEquals(4, tf1.getVcFrameSeq());
    }

    @Test
    public void test2frames_retx() throws Exception {
        fop1ph.initiateADWithVR(3).get();
        fop1ph.onCLCW(getCLCW(false, false, false, 3));
        synchWithExecutor();

        assertNotNull(fop1ph.getFrame());

        TcTransferFrame tf0 = sendTcInOneFrame(89);
        TcTransferFrame tf1 = sendTcInOneFrame(90);

        assertEquals(3, tf0.getVcFrameSeq());
        assertEquals(4, tf1.getVcFrameSeq());

        // ask for retransmit
        fop1ph.onCLCW(getCLCW(false, false, true, 3));

        verifyState(2);
        List<TcTransferFrame> l = getFrames(2, 1000);
        assertEquals(2, l.size());
        assertEquals(tf0, l.get(0));
        assertEquals(tf1, l.get(1));
    }

    @Test
    public void test2frames_retx_invl() throws Exception {
        fop1ph.initiateADWithVR(3).get();
        fop1ph.onCLCW(getCLCW(false, false, false, 3));
        synchWithExecutor();
        assertNotNull(fop1ph.getFrame());

        sendTcInOneFrame(89);
        sendTcInOneFrame(90);

        fop1ph.onCLCW(getCLCW(false, false, true, 3));
        synchWithExecutor();
        fop1ph.onCLCW(getCLCW(false, false, false, 3));

        verifyState(6);
        assertEquals(AlertType.SYNCH, monitor.alerts.get(0));
    }

    @Test
    public void test2frames_retr_timeout() throws Exception {
        fop1ph.setTimeoutType(0);
        fop1ph.setT1Initial(100);
        fop1ph.setTransmissionLimit(2);

        fop1ph.setVs(3);
        fop1ph.initiateAD(false).get();

        sendTcInOneFrame(89);
        sendTcInOneFrame(90);

        fop1ph.onCLCW(getCLCW(false, false, true, 3));
        List<TcTransferFrame> l = getFrames(2, 1000);
        assertEquals(2, l.size());

        fop1ph.onCLCW(getCLCW(false, false, true, 3));
        l = getFrames(2, 1000);
        assertEquals(0, l.size());

        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        verifyState(6);

        fop1ph.terminateAD().get();
    }

    @Test
    public void test2frames_timeout_retr() throws Exception {
        fop1ph.setTimeoutType(0);
        fop1ph.setT1Initial(100);
        fop1ph.setTransmissionLimit(2);

        fop1ph.setVs(3);
        fop1ph.initiateAD(false).get();

        verifyState(1);

        sendTcInOneFrame(89);
        sendTcInOneFrame(90);

        fop1ph.onCLCW(getCLCW(false, false, false, 3));
        List<TcTransferFrame> l = getFrames(2, 1000);
        assertEquals(2, l.size());

        fop1ph.onCLCW(getCLCW(false, false, true, 3));

        assertTrue(monitor.alertSema.tryAcquire(1, 1, TimeUnit.SECONDS));
        verifyState(6);

        fop1ph.terminateAD().get();
    }

    @Test
    public void test3frames_retx() throws Exception {
        fop1ph.setVs(255);
        fop1ph.setTransmissionLimit(2);
        fop1ph.initiateAD(false).get();
        verifyState(1);

        sendTcInOneFrame(89);
        sendTcInOneFrame(90);
        sendTcInOneFrame(91);

        fop1ph.onCLCW(getCLCW(false, false, true, 0));
        verifyState(2);
        List<TcTransferFrame> l = getFrames(5, 1000);

        assertEquals(2, l.size());
        assertEquals(0, l.get(0).getVcFrameSeq());
        assertEquals(1, l.get(1).getVcFrameSeq());
    }

    @Test
    public void test2frames_wait() throws Exception {
        fop1ph.setT1Initial(100);
        fop1ph.initiateADWithVR(3).get();
        fop1ph.onCLCW(getCLCW(false, false, false, 3));
        synchWithExecutor();

        assertNotNull(fop1ph.getFrame());

        sendTcInOneFrame(89);
        sendTcInOneFrame(90);

        // send retransmit with wait
        fop1ph.onCLCW(getCLCW(false, true, true, 3));
        verifyState(3);

        List<TcTransferFrame> l = getFrames(2, 1000);
        assertTrue(l.isEmpty());

        // retransmit with wait but ack one frame
        fop1ph.onCLCW(getCLCW(false, true, true, 4));
        verifyState(3);

        l = getFrames(2, 1000);
        assertTrue(l.isEmpty());

        // retransmit without wait
        fop1ph.onCLCW(getCLCW(false, false, true, 4));
        synchWithExecutor();

        verifyState(2);

        l = getFrames(1, 1000);

        assertEquals(1, l.size());
        assertEquals(4, l.get(0).getVcFrameSeq());
    }

    @Test
    public void testInvl_retx() throws Exception {
        fop1ph.setVs(3);
        fop1ph.initiateAD(false).get();

        sendTcInOneFrame(100);
        fop1ph.onCLCW(getCLCW(false, false, true, 4));

        verifyState(6);
        assertEquals(AlertType.SYNCH, monitor.alerts.get(0));
    }

    @Test
    public void testSuspendResume() throws Exception {
        fop1ph.setTimeoutType(1);
        fop1ph.setT1Initial(100);
        fop1ph.setVs(3);
        fop1ph.initiateAD(false).get();

        Fop1Exception e1 = null;
        // try to resume while not suspended
        try {
            fop1ph.resume().get();
        } catch (ExecutionException e) {
            e1 = (Fop1Exception) e.getCause();
        }
        assertNotNull(e1);
        sendTcInOneFrame(100);

        List<TcTransferFrame> l = getFrames(2, 1000);
        assertEquals(2, l.size());
        assertTrue(monitor.suspendSema.tryAcquire(1, 1, TimeUnit.SECONDS));

        verifyState(6);

        assertTrue(monitor.suspended);
        fop1ph.resume();
        fop1ph.onCLCW(getCLCW(false, false, false, 4));

        verifyState(1);
    }

    @Test
    public void testBrokenSync() throws Exception {
        fop1ph.setVs(3);
        fop1ph.initiateAD(false).get();

        sendTcInOneFrame(100);

        fop1ph.onCLCW(getCLCW(false, false, false, 5));
        verifyState(6);

        assertEquals(AlertType.NNR, monitor.alerts.get(0));
    }

    @Test
    public void testBDFrame() throws Exception {
        fop1ph.sendCommand(makeTc(true, 0, 100, 200));

        TcTransferFrame tf = fop1ph.getFrame();
        assertNotNull(tf);
        assertEquals(1, tf.getCommands().size());
        assertTrue(tf.isBypass());
    }

    @Test
    public void testInvalidReq() throws Exception {
        AtomicInteger errCount = new AtomicInteger();

        fop1ph.suspendState = 1;

        fop1ph.setVs(3).exceptionally(v -> {
            errCount.incrementAndGet();
            return null;
        });
        synchWithExecutor();
        fop1ph.suspendState = 0;

        fop1ph.setVs(3);
        fop1ph.initiateAD(false).get();
        synchWithExecutor();
        assertEquals(1, monitor.state);

        fop1ph.setVs(3).exceptionally(v -> {
            errCount.incrementAndGet();
            return null;
        });

        fop1ph.initiateADWithUnlock().exceptionally(v -> {
            errCount.incrementAndGet();
            return null;
        });

        fop1ph.initiateAD(false).exceptionally(v -> {
            errCount.incrementAndGet();
            return null;
        });

        fop1ph.initiateADWithVR(8).exceptionally(v -> {
            errCount.incrementAndGet();
            return null;
        });

        try {
            fop1ph.setWindowWidth(1000);
        } catch (Exception e) {
            errCount.incrementAndGet();
        }

        try {
            fop1ph.setTimeoutType(5);
        } catch (Exception e) {
            errCount.incrementAndGet();
        }

        try {
            fop1ph.initiateADWithVR(-10);
        } catch (Exception e) {
            errCount.incrementAndGet();
        }

        synchWithExecutor();
        assertEquals(1, monitor.state);

        fop1ph.setWindowWidth(1);

        synchWithExecutor();
        assertEquals(8, errCount.get());
    }

    private void verifyState(int state) throws Exception {
        synchWithExecutor();
        assertEquals(state, monitor.state);
    }

    // execute an empty task in order to make sure the executor has finished executing
    // whatever it was doing when this method is called
    private void synchWithExecutor() throws Exception {
        executor.submit(() -> {
        }).get();
    }

    class MyDownstream {
        boolean autoAckBC = true;
        boolean autoAckAD = true;
        boolean autoAckBD = true;
        List<TcTransferFrame> bcList = new ArrayList<>();
        List<TcTransferFrame> bdList = new ArrayList<>();
        List<TcTransferFrame> adList = new ArrayList<>();
        Semaphore bcSema = new Semaphore(0);
        Semaphore bdSema = new Semaphore(0);
        Semaphore adSema = new Semaphore(0);
        CompletableFuture<Void> bcFuture, adFuture, bdFuture;

        public CompletableFuture<Void> transmitBC(TcTransferFrame frame) {
            bcList.add(frame);
            bcFuture = new CompletableFuture<>();
            bcSema.release();
            if (autoAckBC) {
                bcFuture.complete(null);
            }
            return bcFuture;
        }

        public CompletableFuture<Void> transmitAD(TcTransferFrame frame) {
            adList.add(frame);
            adFuture = new CompletableFuture<>();
            adSema.release();
            if (autoAckAD) {
                adFuture.complete(null);
            }
            return adFuture;
        }

        public CompletableFuture<Void> transmitBD(TcTransferFrame frame) {
            bdList.add(frame);
            bdFuture = new CompletableFuture<>();
            bdSema.release();
            if (autoAckBD) {
                bdFuture.complete(null);
            }
            return bdFuture;
        }

    }

    private TcTransferFrame sendTcInOneFrame(int seqNum) throws Exception {
        PreparedCommand pc = makeTc(false, 100, seqNum, 800);
        fop1ph.sendCommand(pc);
        synchWithExecutor();
        TcTransferFrame tf = fop1ph.getFrame();
        assertNotNull(tf);
        assertEquals(pc, tf.getCommands().get(0));
        return tf;
    }

    int getCLCW(boolean lockout, boolean wait, boolean retransmit, int nR) {
        return (1 << 24) + (vcId << 18) + (bti(lockout) << 13) + (bti(wait) << 12) + (bti(retransmit) << 11) + nR;
    }

    private static int bti(boolean x) {
        return x ? 1 : 0;
    }

    class MyMonitor implements Cop1Monitor {
        boolean suspended;
        int state;
        int suspendedState;
        List<AlertType> alerts = new ArrayList<>();
        Semaphore alertSema = new Semaphore(0);
        Semaphore suspendSema = new Semaphore(0);

        @Override
        public void suspended(int suspendedState) {
            this.suspended = true;
            this.suspendedState = suspendedState;
            suspendSema.release();
        }

        @Override
        public void alert(AlertType alert) {
            alerts.add(alert);
            alertSema.release();
            // System.out.println("MONITOR: alert: " + alert);
        }

        @Override
        public void stateChanged(int oldState, int newState) {
            // System.out.println("MONITOR: Sate changed, new state: " + newState);
            this.state = newState;
        }

        @Override
        public void disabled() {
        }
    }

    private PreparedCommand makeTc(boolean bypass, long t, int seqNum, int length) {
        CommandId id = CommandId.newBuilder().setOrigin("test").setGenerationTime(t).setSequenceNumber(seqNum).build();
        PreparedCommand pc = new PreparedCommand(id);
        if (bypass) {
            pc.addAttribute(CommandHistoryAttribute.newBuilder().setName(Cop1TcPacketHandler.OPTION_BYPASS.getId())
                    .setValue(ValueHelper.newValue(true)).build());
        }
        pc.setBinary(new byte[length]);

        return pc;
    }

    private List<TcTransferFrame> getFrames(int n, long maxTime) throws InterruptedException {
        List<TcTransferFrame> l = new ArrayList<>();
        int i = 0;
        long t0 = System.currentTimeMillis();
        while (i < n) {
            TcTransferFrame tf = fop1ph.getFrame();
            if (tf != null) {
                l.add(tf);
                i++;
            } else {
                if (System.currentTimeMillis() - t0 > maxTime) {
                    break;
                }
                dataAvailable.tryAcquire(maxTime, TimeUnit.MILLISECONDS);
            }
        }
        return l;
    }

}
