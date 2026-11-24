package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.buckets.Bucket;
import org.yamcs.buckets.BucketManager;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.Event;

/**
 * Stops and restarts the CFDP service in the middle of downlinks and checks that the transfers are completed.
 * <p>
 * The test plays the role of the sender by injecting PDUs directly into the cfdp_in stream.
 */
public class CfdpRestartIntegrationTest {
    static final String yamcsInstance = "cfdp-test-inst";
    static final String SERVICE_NAME = "CfdpService";
    static final int REMOTE_ID = 15;
    static final int LOCAL_ID = 12;
    static final int PDU_SIZE = 500;

    static Bucket incomingBucket;
    static Path transferDir;
    static int seqNum = 100;

    Random random = new Random(1);
    Stream cfdpIn;
    Stream cfdpOut;
    CfdpService service;
    BlockingQueue<CfdpPacket> outPdus = new LinkedBlockingQueue<>();
    StreamSubscriber outSubscriber;

    @BeforeAll
    public static void beforeClass() throws Exception {
        EventProducerFactory.setMockup(true);
        BucketManager.setMockup();
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "yamcs-cfdp-data");
        FileUtils.deleteRecursivelyIfExists(dataDir);
        YConfiguration.setupTest("cfdp");
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();
        incomingBucket = YamcsServer.getServer().getBucketManager().getBucket("cfdpDown");

        // we do not use the service started by the server, but our own instances
        CfdpService svc = YamcsServer.getServer().getService(yamcsInstance, CfdpService.class);
        svc.stopAsync().awaitTerminated();
        transferDir = Path.of(YarchDatabase.getInstance(yamcsInstance).getRoot(), "cfdp", SERVICE_NAME);
    }

    @AfterAll
    public static void afterClass() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    @BeforeEach
    public void before() throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        cfdpIn = ydb.getStream("cfdp_in");
        cfdpOut = ydb.getStream("cfdp_out");
        outSubscriber = new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                outPdus.add(CfdpPacket.fromTuple(tuple));
            }

            @Override
            public void streamClosed(Stream stream) {
            }
        };
        cfdpOut.addSubscriber(outSubscriber);
        EventProducerFactory.getMockupQueue().clear();
    }

    @AfterEach
    public void after() throws Exception {
        cfdpOut.removeSubscriber(outSubscriber);
        if (service != null && service.isRunning()) {
            service.stopAsync().awaitTerminated();
        }
        // make sure a failed test does not leave transfers to be restored by the next one
        FileUtils.deleteRecursivelyIfExists(transferDir);
    }

    // the buckets survive between test runs, use unique object names
    static String uniqueName(String name) {
        return name + "-" + System.currentTimeMillis();
    }

    static YConfiguration serviceConfig(boolean persist) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("inStream", "cfdp_in");
        m.put("outStream", "cfdp_out");
        m.put("localEntities", List.of(Map.of("name", "local12", "id", LOCAL_ID)));
        m.put("remoteEntities", List.of(Map.of("name", "remote15", "id", REMOTE_ID)));
        m.put("sleepBetweenPdus", 10);
        m.put("finAckTimeout", 1000);
        m.put("finAckLimit", 10);
        m.put("nakTimeout", 300);
        m.put("inactivityTimeout", 60000);
        m.put("pendingAfterCompletion", 300);
        m.put("syncInterval", 200);
        m.put("persistTransfers", persist);
        CfdpService s = new CfdpService();
        return s.getSpec().validate(YConfiguration.wrap(m));
    }

    CfdpService startService(boolean persist) throws Exception {
        service = new CfdpService();
        service.init(yamcsInstance, SERVICE_NAME, serviceConfig(persist));
        service.startAsync().awaitRunning();
        return service;
    }

    void stopService() {
        service.stopAsync().awaitTerminated();
    }

    @Test
    public void testClass2Restart() throws Exception {
        startService(true);
        int seq = ++seqNum;
        byte[] data = randomData(10_000);
        String restartClass2 = uniqueName("restart-class2");
        CfdpHeader hdr = header(seq, true, true);

        send(new MetadataPacket(false, ChecksumType.MODULAR, data.length, "src2", restartClass2, null,
                hdr.copy(true)));
        // send data up to 6000, except 2000-3000
        sendData(hdr, data, 0, 2000);
        sendData(hdr, data, 3000, 6000);
        sync();

        CfdpIncomingTransfer trsf = (CfdpIncomingTransfer) service.getCfdpTransfer(hdr.getTransactionId());
        assertNotNull(trsf);
        long id = trsf.getId();
        assertEquals(5000, trsf.getTransferredSize());

        stopService();
        assertTrue(Files.exists(transferDir.resolve(id + ".state")));
        assertTrue(Files.exists(transferDir.resolve(id + ".data")));
        // the transfer is still running in the database
        assertEquals(TransferState.RUNNING, searchArchive(id).getTransferState());
        outPdus.clear();

        startService(true);
        verifyEvent("after restart");
        CfdpIncomingTransfer trsf2 = (CfdpIncomingTransfer) service.getCfdpTransfer(hdr.getTransactionId());
        assertNotNull(trsf2);
        assertEquals(id, trsf2.getId());
        assertEquals(5000, trsf2.getTransferredSize());
        assertEquals(data.length, trsf2.getTotalSize());
        assertEquals(restartClass2, trsf2.getObjectName());

        // the receiver should immediately ask for the missing data (before EOF only the gaps are requested)
        NakPacket nak = waitFor(NakPacket.class);
        assertEquals(List.of("2000-3000"), nak.getSegmentRequests().stream().map(SegmentRequest::toString).toList());

        sendData(hdr, data, 2000, 3000);
        sendData(hdr, data, 6000, data.length);
        send(new EofPacket(ConditionCode.NO_ERROR, ModularChecksumCalculator.calculateChecksum(data), data.length,
                null, hdr.copy(true)));

        AckPacket eofAck = waitFor(AckPacket.class);
        assertEquals(FileDirectiveCode.EOF, eofAck.getDirectiveCode());
        FinishedPacket fin = waitFor(FinishedPacket.class);
        assertEquals(ConditionCode.NO_ERROR, fin.getConditionCode());

        // the file is saved before the Finished PDU is sent
        assertArrayEquals(data, incomingBucket.getObjectAsync(restartClass2).get());
        sendFinAck(hdr);
        waitForState(trsf2, TransferState.COMPLETED);

        // the temporary files are removed after pendingAfterCompletion
        waitForDeletion(id);
        assertEquals(TransferState.COMPLETED, searchArchive(id).getTransferState());
    }

    @Test
    public void testClass1Restart() throws Exception {
        startService(true);
        int seq = ++seqNum;
        byte[] data = randomData(7_777);
        String restartClass1 = uniqueName("restart-class1");
        CfdpHeader hdr = header(seq, false, false);

        send(new MetadataPacket(false, ChecksumType.MODULAR, data.length, "src1", restartClass1, null,
                hdr.copy(true)));
        sendData(hdr, data, 0, 4000);
        sync();
        long id = service.getCfdpTransfer(hdr.getTransactionId()).getId();
        stopService();

        startService(true);
        CfdpIncomingTransfer trsf2 = (CfdpIncomingTransfer) service.getCfdpTransfer(hdr.getTransactionId());
        assertNotNull(trsf2);
        assertEquals(4000, trsf2.getTransferredSize());

        sendData(hdr, data, 4000, data.length);
        send(new EofPacket(ConditionCode.NO_ERROR, ModularChecksumCalculator.calculateChecksum(data), data.length,
                null, hdr.copy(true)));
        waitForState(trsf2, TransferState.COMPLETED);
        assertArrayEquals(data, incomingBucket.getObjectAsync(restartClass1).get());
        // no PDU is sent by the receiver in class 1
        assertNull(outPdus.poll());
        waitForDeletion(id);
    }

    @Test
    public void testRestartInFinState() throws Exception {
        startService(true);
        int seq = ++seqNum;
        byte[] data = randomData(3000);
        String restartFin = uniqueName("restart-fin");
        CfdpHeader hdr = header(seq, true, true);

        send(new MetadataPacket(false, ChecksumType.MODULAR, data.length, "src3", restartFin, null,
                hdr.copy(true)));
        sendData(hdr, data, 0, data.length);
        send(new EofPacket(ConditionCode.NO_ERROR, ModularChecksumCalculator.calculateChecksum(data), data.length,
                null, hdr.copy(true)));
        waitFor(FinishedPacket.class);
        long id = service.getCfdpTransfer(hdr.getTransactionId()).getId();
        // stop before the Finished PDU is acknowledged
        stopService();
        outPdus.clear();

        startService(true);
        CfdpIncomingTransfer trsf2 = (CfdpIncomingTransfer) service.getCfdpTransfer(hdr.getTransactionId());
        assertNotNull(trsf2);
        // the Finished PDU is sent again
        FinishedPacket fin = waitFor(FinishedPacket.class);
        assertEquals(ConditionCode.NO_ERROR, fin.getConditionCode());
        sendFinAck(hdr);
        waitForState(trsf2, TransferState.COMPLETED);
        assertArrayEquals(data, incomingBucket.getObjectAsync(restartFin).get());
        waitForDeletion(id);
    }

    @Test
    public void testRestartSuspended() throws Exception {
        startService(true);
        int seq = ++seqNum;
        byte[] data = randomData(3000);
        String restartSuspended = uniqueName("restart-suspended");
        CfdpHeader hdr = header(seq, true, true);

        send(new MetadataPacket(false, ChecksumType.MODULAR, data.length, "src4", restartSuspended, null,
                hdr.copy(true)));
        sendData(hdr, data, 0, 1000);
        sync();
        CfdpIncomingTransfer trsf = (CfdpIncomingTransfer) service.getCfdpTransfer(hdr.getTransactionId());
        service.pause(trsf);
        stopService();
        outPdus.clear();

        startService(true);
        CfdpIncomingTransfer trsf2 = (CfdpIncomingTransfer) service.getCfdpTransfer(hdr.getTransactionId());
        assertEquals(TransferState.PAUSED, trsf2.getTransferState());
        Thread.sleep(500);
        // no NAK while suspended
        assertNull(outPdus.poll());

        // data received while suspended is stored
        sendData(hdr, data, 1000, data.length);
        send(new EofPacket(ConditionCode.NO_ERROR, ModularChecksumCalculator.calculateChecksum(data), data.length,
                null, hdr.copy(true)));
        sync();
        assertEquals(TransferState.PAUSED, trsf2.getTransferState());

        service.resume(trsf2);
        waitFor(FinishedPacket.class);
        sendFinAck(hdr);
        waitForState(trsf2, TransferState.COMPLETED);
        assertArrayEquals(data, incomingBucket.getObjectAsync(restartSuspended).get());
    }

    @Test
    public void testUnrestorableTransfer() throws Exception {
        // a transfer whose state is corrupted
        Files.createDirectories(transferDir);
        Files.write(transferDir.resolve("987654.state"), new byte[] { 1, 2, 3, 4, 5 });
        Files.write(transferDir.resolve("987654.data"), new byte[] { 1, 2, 3, 4, 5 });
        // data without state
        Files.write(transferDir.resolve("987655.data"), new byte[] { 1, 2, 3, 4, 5 });

        startService(true);
        verifyEvent("could not be restored");
        service.store.flush();
        assertFalse(Files.exists(transferDir.resolve("987654.state")));
        assertFalse(Files.exists(transferDir.resolve("987654.data")));
        assertFalse(Files.exists(transferDir.resolve("987655.data")));
        FileTransfer ft = searchArchive(987654);
        assertEquals(TransferState.FAILED, ft.getTransferState());
        assertTrue(ft.getFailuredReason().contains("could not be restored"));
    }

    @Test
    public void testNoPersistence() throws Exception {
        startService(false);
        int seq = ++seqNum;
        byte[] data = randomData(3000);
        String noPersist = uniqueName("no-persist");
        CfdpHeader hdr = header(seq, true, true);
        send(new MetadataPacket(false, ChecksumType.MODULAR, data.length, "src5", noPersist, null,
                hdr.copy(true)));
        sendData(hdr, data, 0, 1000);
        sync();
        long id = service.getCfdpTransfer(hdr.getTransactionId()).getId();
        stopService();

        FileTransfer ft = searchArchive(id);
        assertEquals(TransferState.FAILED, ft.getTransferState());
        assertTrue(ft.getFailuredReason().contains("service shutdown"));
        assertFalse(Files.exists(transferDir.resolve(id + ".state")));
        assertFalse(Files.exists(transferDir.resolve(id + ".data")));

        startService(false);
        assertNull(service.getCfdpTransfer(hdr.getTransactionId()));
    }

    private CfdpHeader header(int seq, boolean fileDirective, boolean acknowledged) {
        return new CfdpHeader(fileDirective, false, acknowledged, false, 2, 3, REMOTE_ID, LOCAL_ID, seq);
    }

    private void send(CfdpPacket packet) {
        cfdpIn.emitTuple(packet.toTuple(TimeEncoding.getWallclockTime()));
    }

    private void sendData(CfdpHeader hdr, byte[] data, int start, int end) {
        CfdpHeader dataHdr = hdr.copy(false);
        for (int off = start; off < end; off += PDU_SIZE) {
            int e = Math.min(end, off + PDU_SIZE);
            send(new FileDataPacket(Arrays.copyOfRange(data, off, e), off, dataHdr));
        }
    }

    private void sendFinAck(CfdpHeader hdr) {
        send(new AckPacket(FileDirectiveCode.FINISHED, FileDirectiveSubtypeCode.FINISHED_BY_END_SYSTEM,
                ConditionCode.NO_ERROR, TransactionStatus.TERMINATED, hdr.copy(true)));
    }

    // wait for the service to process all the PDUs
    private void sync() throws Exception {
        for (int i = 0; i < 3; i++) {
            service.getExecutor().submit(() -> {
            }).get();
        }
        service.store.flush();
    }

    @SuppressWarnings("unchecked")
    private <T extends CfdpPacket> T waitFor(Class<T> type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        List<CfdpPacket> skipped = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            CfdpPacket p = outPdus.poll(100, TimeUnit.MILLISECONDS);
            if (p == null) {
                continue;
            }
            if (type.isInstance(p)) {
                return (T) p;
            }
            skipped.add(p);
        }
        throw new AssertionError("Timeout waiting for " + type.getSimpleName() + "; received: " + skipped);
    }

    private void waitForState(OngoingCfdpTransfer trsf, TransferState state) throws InterruptedException {
        for (int i = 0; i < 50 && trsf.getTransferState() != state; i++) {
            Thread.sleep(100);
        }
        assertEquals(state, trsf.getTransferState(), "failure reason: " + trsf.getFailuredReason());
    }

    private void waitForDeletion(long id) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!Files.exists(transferDir.resolve(id + ".data"))
                    && !Files.exists(transferDir.resolve(id + ".state"))
                    && !Files.exists(transferDir.resolve(id + ".journal"))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The files of transfer " + id + " have not been deleted");
    }

    private FileTransfer searchArchive(long id) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        // the DB stream is processed asynchronously
        Thread.sleep(200);
        var res = ydb.execute("select * from cfdp where id=?", id);
        assertTrue(res.hasNext(), "transfer " + id + " not found in the database");
        var ct = new CompletedTransfer(res.next());
        res.close();
        return ct;
    }

    private byte[] randomData(int size) {
        byte[] b = new byte[size];
        random.nextBytes(b);
        return b;
    }

    private void verifyEvent(String evs) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            for (Event ev : EventProducerFactory.getMockupQueue()) {
                if (ev.getMessage().contains(evs)) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Event containing '" + evs + "' not found in " + EventProducerFactory
                .getMockupQueue().stream().map(Event::getMessage).toList());
    }
}
