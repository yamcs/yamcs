package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.RandomAccessFile;
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
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.TransferOptions;
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
 * Stops and restarts the CFDP service in the middle of uplinks and checks that the transfers are continued; also tests
 * the autoResume option in both directions.
 * <p>
 * The test plays the role of the remote entity by reading the PDUs from the cfdp_out stream and injecting PDUs
 * directly into the cfdp_in stream.
 */
public class CfdpUplinkRestartIntegrationTest {
    static final String yamcsInstance = "cfdp-test-inst";
    static final String SERVICE_NAME = "CfdpService";
    static final int REMOTE_ID = 15;
    static final int LOCAL_ID = 12;
    static final int MAX_PDU_SIZE = 512;
    // maxPduSize - header (4 + 2*2 + 3) - offset (4)
    static final int DATA_PER_PDU = MAX_PDU_SIZE - 4 - 2 * 2 - 3 - 4;

    static Bucket outBucket;
    static Path transferDir;
    static int seqNum = 300;

    Random random = new Random(2);
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
        var bm = YamcsServer.getServer().getBucketManager();
        outBucket = bm.getBucket("cfdp-up-restart");
        if (outBucket == null) {
            outBucket = bm.createBucket("cfdp-up-restart");
        }

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
        FileUtils.deleteRecursivelyIfExists(transferDir);
    }

    @AfterEach
    public void after() throws Exception {
        cfdpOut.removeSubscriber(outSubscriber);
        if (service != null && service.isRunning()) {
            service.stopAsync().awaitTerminated();
        }
        FileUtils.deleteRecursivelyIfExists(transferDir);
    }

    static YConfiguration serviceConfig(Map<String, Object> overrides) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("inStream", "cfdp_in");
        m.put("outStream", "cfdp_out");
        m.put("localEntities", List.of(Map.of("name", "local12", "id", LOCAL_ID)));
        m.put("remoteEntities", List.of(Map.of("name", "remote15", "id", REMOTE_ID)));
        m.put("entityIdLength", 2);
        m.put("sequenceNrLength", 3);
        m.put("maxPduSize", MAX_PDU_SIZE);
        m.put("sleepBetweenPdus", 10);
        m.put("eofAckTimeout", 1000);
        m.put("eofAckLimit", 10);
        m.put("finAckTimeout", 1000);
        m.put("finAckLimit", 10);
        m.put("nakTimeout", 300);
        m.put("inactivityTimeout", 60000);
        m.put("pendingAfterCompletion", 300);
        m.put("syncInterval", 200);
        m.put("persistTransfers", true);
        m.putAll(overrides);
        CfdpService s = new CfdpService();
        return s.getSpec().validate(YConfiguration.wrap(m));
    }

    CfdpService startService() throws Exception {
        return startService(Map.of());
    }

    CfdpService startService(Map<String, Object> overrides) throws Exception {
        service = new CfdpService();
        service.init(yamcsInstance, SERVICE_NAME, serviceConfig(overrides));
        service.startAsync().awaitRunning();
        return service;
    }

    void stopService() {
        service.stopAsync().awaitTerminated();
    }

    CfdpFileTransfer upload(String objName, byte[] data, boolean reliable, boolean closure) throws Exception {
        outBucket.putObjectAsync(objName, null, null, data).get();
        TransferOptions opts = new TransferOptions();
        opts.setReliable(reliable);
        opts.setClosureRequested(closure);
        return service.startUpload("local12", outBucket, objName, "remote15", "/dst/" + objName, opts);
    }

    // ------------------------------------------------------------------------------------------------------------

    @Test
    public void testClass2RestartMidTransfer() throws Exception {
        startService();
        byte[] data = randomData(30_000);
        String objName = uniqueName("up-class2");
        var trsf = (CfdpOutgoingTransfer) upload(objName, data, true, false);
        long id = trsf.getId();

        Receiver rx = new Receiver(data.length);
        MetadataPacket md = rx.waitFor(MetadataPacket.class);
        assertEquals(data.length, md.getFileLength());
        rx.waitForData(10);
        stopService();
        rx.drain();

        assertTrue(Files.exists(transferDir.resolve(id + ".state")));
        assertTrue(Files.exists(transferDir.resolve(id + ".data")));
        assertEquals(TransferState.RUNNING, searchArchive(id).getTransferState());
        long sentBeforeStop = rx.maxEnd;
        assertTrue(sentBeforeStop < data.length, "the transfer finished before the stop");

        startService();
        verifyEvent("after restart");
        var trsf2 = (CfdpOutgoingTransfer) service.getCfdpTransfer(trsf.getTransactionId());
        assertNotNull(trsf2);
        assertEquals(id, trsf2.getId());
        assertEquals(data.length, trsf2.getTotalSize());
        assertEquals(objName, trsf2.getObjectName());

        // the transfer continues where it stopped: no data is sent twice, no metadata is sent again
        FileDataPacket fd = rx.waitFor(FileDataPacket.class);
        assertEquals(sentBeforeStop, fd.getOffset());

        EofPacket eof = rx.waitFor(EofPacket.class);
        assertEquals(ConditionCode.NO_ERROR, eof.getConditionCode());
        assertEquals(data.length, eof.getFileLength());
        assertEquals(ModularChecksumCalculator.calculateChecksum(data), eof.getFileChecksum());
        assertEquals(0, rx.duplicates);
        assertArrayEquals(data, rx.data);

        rx.sendEofAck(eof);
        rx.sendFinished(eof, ConditionCode.NO_ERROR);
        AckPacket finAck = rx.waitFor(AckPacket.class);
        assertEquals(FileDirectiveCode.FINISHED, finAck.getDirectiveCode());
        waitForState(trsf2, TransferState.COMPLETED);
        waitForDeletion(id);
        assertEquals(TransferState.COMPLETED, searchArchive(id).getTransferState());
    }

    @Test
    public void testClass1RestartMidTransfer() throws Exception {
        startService();
        byte[] data = randomData(20_000);
        String objName = uniqueName("up-class1");
        var trsf = (CfdpOutgoingTransfer) upload(objName, data, false, false);
        long id = trsf.getId();

        Receiver rx = new Receiver(data.length);
        rx.waitForData(5);
        stopService();
        rx.drain();

        startService();
        var trsf2 = (CfdpOutgoingTransfer) service.getCfdpTransfer(trsf.getTransactionId());
        assertNotNull(trsf2);
        EofPacket eof = rx.waitFor(EofPacket.class);
        assertEquals(ModularChecksumCalculator.calculateChecksum(data), eof.getFileChecksum());
        assertArrayEquals(data, rx.data);
        assertEquals(0, rx.duplicates);
        waitForState(trsf2, TransferState.COMPLETED);
        waitForDeletion(id);
    }

    @Test
    public void testRestartWithPendingNaksWhileSuspended() throws Exception {
        startService();
        byte[] data = randomData(5_000);
        String objName = uniqueName("up-nak");
        var trsf = (CfdpOutgoingTransfer) upload(objName, data, true, false);
        long id = trsf.getId();

        Receiver rx = new Receiver(data.length);
        EofPacket eof = rx.waitFor(EofPacket.class);
        rx.sendEofAck(eof);
        sync();
        service.pause(trsf);
        assertEquals(TransferState.PAUSED, trsf.getTransferState());

        // NAK received while suspended: the segments are remembered but not sent
        rx.sendNak(eof, new SegmentRequest(1000, 2000), new SegmentRequest(3000, 3200));
        sync();
        assertEquals("[1000-2000, 3000-3200]", trsf.getToResend().toString());
        stopService();
        rx.drain();
        outPdus.clear();

        startService();
        var trsf2 = (CfdpOutgoingTransfer) service.getCfdpTransfer(trsf.getTransactionId());
        assertNotNull(trsf2);
        assertEquals(TransferState.PAUSED, trsf2.getTransferState());
        assertEquals("[1000-2000, 3000-3200]", trsf2.getToResend().toString());
        Thread.sleep(300);
        // nothing is sent while suspended
        assertNull(outPdus.poll());

        service.resume(trsf2);
        List<FileDataPacket> resent = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            resent.add(rx.waitFor(FileDataPacket.class));
        }
        int e1 = 1000 + DATA_PER_PDU, e2 = e1 + DATA_PER_PDU;
        assertEquals(List.of("1000-" + e1, e1 + "-" + e2, e2 + "-2000", "3000-3200"),
                resent.stream().map(p -> p.getOffset() + "-" + p.getEndOffset()).toList());
        Thread.sleep(200);
        assertNull(pollData(), "only the requested segments are sent");

        rx.sendFinished(eof, ConditionCode.NO_ERROR);
        waitForState(trsf2, TransferState.COMPLETED);
        waitForDeletion(id);
    }

    @Test
    public void testRestartSuspendedMidTransfer() throws Exception {
        startService();
        byte[] data = randomData(20_000);
        String objName = uniqueName("up-suspended");
        var trsf = (CfdpOutgoingTransfer) upload(objName, data, true, false);

        Receiver rx = new Receiver(data.length);
        rx.waitForData(5);
        service.pause(trsf);
        stopService();
        rx.drain();
        outPdus.clear();

        startService();
        var trsf2 = (CfdpOutgoingTransfer) service.getCfdpTransfer(trsf.getTransactionId());
        assertEquals(TransferState.PAUSED, trsf2.getTransferState());
        Thread.sleep(300);
        assertNull(outPdus.poll());

        service.resume(trsf2);
        EofPacket eof = rx.waitFor(EofPacket.class);
        assertArrayEquals(data, rx.data);
        assertEquals(0, rx.duplicates);
        rx.sendEofAck(eof);
        rx.sendFinished(eof, ConditionCode.NO_ERROR);
        waitForState(trsf2, TransferState.COMPLETED);
    }

    @Test
    public void testRestartWithQueuedTransfers() throws Exception {
        startService(Map.of("maxNumPendingUploads", 1));
        byte[] data1 = randomData(20_000);
        byte[] data2 = randomData(3_000);
        String objName1 = uniqueName("up-q1");
        String objName2 = uniqueName("up-q2");
        var trsf1 = (CfdpOutgoingTransfer) upload(objName1, data1, false, false);
        var queued = upload(objName2, data2, false, false);
        assertInstanceOf(QueuedCfdpOutgoingTransfer.class, queued);
        assertEquals(data2.length, queued.getTotalSize());
        long id2 = queued.getId();

        Receiver rx1 = new Receiver(data1.length);
        rx1.waitForData(3);
        stopService();
        rx1.drain();
        // the queued transfer is saved without data
        assertTrue(Files.exists(transferDir.resolve(id2 + ".state")));
        assertFalse(Files.exists(transferDir.resolve(id2 + ".data")));

        // the object is changed after the request: the version at the start of the transfer is sent
        data2 = randomData(4_000);
        outBucket.putObjectAsync(objName2, null, null, data2).get();

        startService(Map.of("maxNumPendingUploads", 1));
        FileTransfer ft = service.getFileTransfer(id2);
        assertInstanceOf(QueuedCfdpOutgoingTransfer.class, ft);
        assertEquals(TransferState.QUEUED, ft.getTransferState());
        assertEquals(objName2, ft.getObjectName());

        EofPacket eof1 = rx1.waitFor(EofPacket.class);
        assertEquals(trsf1.getTransactionId(), eof1.getTransactionId());
        assertArrayEquals(data1, rx1.data);

        // the first one is finished, the queued one starts
        Receiver rx2 = new Receiver(data2.length);
        MetadataPacket md2 = rx2.waitFor(MetadataPacket.class);
        assertEquals("/dst/" + objName2, md2.getDestinationFilename());
        EofPacket eof2 = rx2.waitFor(EofPacket.class);
        assertArrayEquals(data2, rx2.data);
        var trsf2 = (OngoingCfdpTransfer) service.getCfdpTransfer(eof2.getTransactionId());
        assertEquals(id2, trsf2.getId());
        waitForState(trsf2, TransferState.COMPLETED);
        waitForDeletion(id2);
    }

    @Test
    public void testCorruptedDataFile() throws Exception {
        long id = restartWithModifiedDataFile(path -> {
            try (var raf = new RandomAccessFile(path.toFile(), "rw")) {
                raf.seek(100);
                int b = raf.read();
                raf.seek(100);
                raf.write(b ^ 0xFF);
            }
        });
        verifyEvent("could not be restored");
        FileTransfer ft = searchArchive(id);
        assertEquals(TransferState.FAILED, ft.getTransferState());
        assertTrue(ft.getFailuredReason().contains("is corrupted: checksum"), ft.getFailuredReason());
    }

    @Test
    public void testTruncatedDataFile() throws Exception {
        long id = restartWithModifiedDataFile(path -> {
            try (var raf = new RandomAccessFile(path.toFile(), "rw")) {
                raf.setLength(1000);
            }
        });
        FileTransfer ft = searchArchive(id);
        assertEquals(TransferState.FAILED, ft.getTransferState());
        assertTrue(ft.getFailuredReason().contains("has been modified: size is 1000 instead of 20000"),
                ft.getFailuredReason());
    }

    @Test
    public void testMissingDataFile() throws Exception {
        long id = restartWithModifiedDataFile(Files::delete);
        FileTransfer ft = searchArchive(id);
        assertEquals(TransferState.FAILED, ft.getTransferState());
        assertTrue(ft.getFailuredReason().contains("is missing"), ft.getFailuredReason());
    }

    interface PathModifier {
        void modify(Path p) throws Exception;
    }

    private long restartWithModifiedDataFile(PathModifier modifier) throws Exception {
        startService();
        byte[] data = randomData(20_000);
        var trsf = (CfdpOutgoingTransfer) upload(uniqueName("up-corrupt"), data, true, false);
        long id = trsf.getId();
        Receiver rx = new Receiver(data.length);
        rx.waitForData(3);
        stopService();

        modifier.modify(transferDir.resolve(id + ".data"));
        startService();
        assertNull(service.getCfdpTransfer(trsf.getTransactionId()));
        service.store.flush();
        assertFalse(Files.exists(transferDir.resolve(id + ".state")));
        return id;
    }

    @Test
    public void testNoPersistence() throws Exception {
        startService(Map.of("persistTransfers", false));
        byte[] data = randomData(20_000);
        var trsf = (CfdpOutgoingTransfer) upload(uniqueName("up-nopersist"), data, true, false);
        long id = trsf.getId();
        Receiver rx = new Receiver(data.length);
        rx.waitForData(3);
        stopService();
        FileTransfer ft = searchArchive(id);
        assertEquals(TransferState.FAILED, ft.getTransferState());
        assertTrue(ft.getFailuredReason().contains("service shutdown"));
        for (int i = 0; i < 20 && Files.exists(transferDir.resolve(id + ".data")); i++) {
            Thread.sleep(50);
        }
        assertFalse(Files.exists(transferDir.resolve(id + ".data")));
        assertFalse(Files.exists(transferDir.resolve(id + ".state")));
    }

    @Test
    public void testMissingObject() throws Exception {
        startService();
        byte[] data = randomData(1000);
        String objName = uniqueName("up-deleted");
        var trsf = (CfdpOutgoingTransfer) upload(objName, data, true, false);
        // the object is read when the transfer starts; if it disappears in between, the transfer fails
        // (the race is unlikely to be won by the delete, so we only check the outcome if it is)
        outBucket.deleteObjectAsync(objName).get();
        for (int i = 0; i < 50 && trsf.getTransferState() == TransferState.RUNNING; i++) {
            Thread.sleep(20);
        }
        if (trsf.getTransferState() == TransferState.FAILED) {
            assertTrue(trsf.getFailuredReason().contains("Cannot read the file to be sent"),
                    trsf.getFailuredReason());
        }
    }

    @Test
    public void testSenderAutoResume() throws Exception {
        startService(Map.of("autoResume", true, "eofAckTimeout", 100, "eofAckLimit", 2,
                "senderFaultHandlers", Map.of("AckLimitReached", "suspend")));
        byte[] data = randomData(3_000);
        var trsf = (CfdpOutgoingTransfer) upload(uniqueName("up-autoresume"), data, true, false);

        Receiver rx = new Receiver(data.length);
        // the EOF is not acknowledged (e.g. end of the pass) -> the transfer is suspended
        EofPacket eof = rx.waitFor(EofPacket.class);
        waitForState(trsf, TransferState.PAUSED);
        verifyEvent("transfer suspended");
        outPdus.clear();

        // the receiver comes back with a NAK: the transfer resumes
        rx.sendNak(eof, new SegmentRequest(500, 1000));
        FileDataPacket fd = rx.waitFor(FileDataPacket.class);
        assertEquals(500, fd.getOffset());
        assertEquals(TransferState.RUNNING, trsf.getTransferState());
        verifyEvent("transfer resumed");
        EofPacket eof2 = rx.waitFor(EofPacket.class);
        rx.sendEofAck(eof2);
        rx.sendFinished(eof2, ConditionCode.NO_ERROR);
        waitForState(trsf, TransferState.COMPLETED);
    }

    @Test
    public void testSenderNoAutoResume() throws Exception {
        startService(Map.of("eofAckTimeout", 100, "eofAckLimit", 2,
                "senderFaultHandlers", Map.of("AckLimitReached", "suspend")));
        byte[] data = randomData(3_000);
        var trsf = (CfdpOutgoingTransfer) upload(uniqueName("up-noautoresume"), data, true, false);
        Receiver rx = new Receiver(data.length);
        EofPacket eof = rx.waitFor(EofPacket.class);
        waitForState(trsf, TransferState.PAUSED);
        outPdus.clear();
        rx.sendNak(eof, new SegmentRequest(500, 1000));
        Thread.sleep(300);
        assertEquals(TransferState.PAUSED, trsf.getTransferState());
        assertNull(pollData());
        service.cancel(trsf);
    }

    @Test
    public void testReceiverAutoResume() throws Exception {
        startService(Map.of("autoResume", true));
        int seq = ++seqNum;
        byte[] data = randomData(3000);
        CfdpHeader hdr = new CfdpHeader(true, false, true, false, 2, 3, REMOTE_ID, LOCAL_ID, seq);
        String objName = uniqueName("down-autoresume");
        send(new MetadataPacket(false, ChecksumType.MODULAR, data.length, "src", objName, null, hdr.copy(true)));
        sendData(hdr, data, 0, 1000);
        sync();
        var trsf = (CfdpIncomingTransfer) service.getCfdpTransfer(hdr.getTransactionId());
        service.pause(trsf);
        assertEquals(TransferState.PAUSED, trsf.getTransferState());

        // the sender continues: the transfer is resumed
        sendData(hdr, data, 1000, data.length);
        sync();
        assertEquals(TransferState.RUNNING, trsf.getTransferState());
        send(new EofPacket(ConditionCode.NO_ERROR, ModularChecksumCalculator.calculateChecksum(data), data.length,
                null, hdr.copy(true)));
        FinishedPacket fin = waitForPdu(FinishedPacket.class);
        assertEquals(ConditionCode.NO_ERROR, fin.getConditionCode());
        send(new AckPacket(FileDirectiveCode.FINISHED, FileDirectiveSubtypeCode.FINISHED_BY_END_SYSTEM,
                ConditionCode.NO_ERROR, TransactionStatus.TERMINATED, hdr.copy(true)));
        waitForState(trsf, TransferState.COMPLETED);
        Bucket inBucket = YamcsServer.getServer().getBucketManager().getBucket("cfdpDown");
        assertArrayEquals(data, inBucket.getObjectAsync(objName).get());
    }

    // ------------------------------------------------------------------------------------------------------------

    /**
     * Plays the receiver: collects the File Data PDUs sent by the service
     */
    class Receiver {
        final byte[] data;
        long maxEnd = 0;
        int numData = 0;
        int duplicates = 0;
        boolean[] received;

        Receiver(int size) {
            data = new byte[size];
            received = new boolean[size];
        }

        void onData(FileDataPacket fd) {
            numData++;
            int off = (int) fd.getOffset();
            byte[] d = fd.getData();
            boolean dup = true;
            for (int i = 0; i < d.length; i++) {
                if (!received[off + i]) {
                    dup = false;
                    received[off + i] = true;
                }
            }
            if (dup) {
                duplicates++;
            }
            System.arraycopy(d, 0, data, off, d.length);
            maxEnd = Math.max(maxEnd, fd.getEndOffset());
        }

        /**
         * Waits for a PDU of the given type, recording all the file data received meanwhile (including the one
         * returned).
         */
        @SuppressWarnings("unchecked")
        <T extends CfdpPacket> T waitFor(Class<T> type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 10000;
            List<CfdpPacket> skipped = new ArrayList<>();
            while (System.currentTimeMillis() < deadline) {
                CfdpPacket p = outPdus.poll(100, TimeUnit.MILLISECONDS);
                if (p == null) {
                    continue;
                }
                if (p instanceof FileDataPacket fd) {
                    onData(fd);
                }
                if (type.isInstance(p)) {
                    return (T) p;
                }
                if (!(p instanceof FileDataPacket)) {
                    skipped.add(p);
                }
            }
            throw new AssertionError("Timeout waiting for " + type.getSimpleName() + "; received: " + skipped);
        }

        void waitForData(int n) throws InterruptedException {
            while (numData < n) {
                waitFor(FileDataPacket.class);
            }
        }

        void drain() {
            CfdpPacket p;
            while ((p = outPdus.poll()) != null) {
                if (p instanceof FileDataPacket fd) {
                    onData(fd);
                }
            }
        }

        void sendEofAck(EofPacket eof) {
            send(new AckPacket(FileDirectiveCode.EOF, FileDirectiveSubtypeCode.FINISHED_BY_WAYPOINT_OR_OTHER, eof.getConditionCode(),
                    TransactionStatus.ACTIVE, replyHeader(eof)));
        }

        void sendFinished(EofPacket eof, ConditionCode code) {
            send(new FinishedPacket(code, true, FileStatus.SUCCESSFUL_RETENTION, null,
                    replyHeader(eof)));
        }

        void sendNak(EofPacket eof, SegmentRequest... segments) {
            List<SegmentRequest> l = Arrays.asList(segments);
            send(new NakPacket(0, eof.getFileLength(), l, replyHeader(eof)));
        }

        CfdpHeader replyHeader(CfdpPacket p) {
            return p.getHeader().copy(true, true);
        }
    }

    private FileDataPacket pollData() {
        CfdpPacket p;
        while ((p = outPdus.poll()) != null) {
            if (p instanceof FileDataPacket fd) {
                return fd;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T extends CfdpPacket> T waitForPdu(Class<T> type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            CfdpPacket p = outPdus.poll(100, TimeUnit.MILLISECONDS);
            if (type.isInstance(p)) {
                return (T) p;
            }
        }
        throw new AssertionError("Timeout waiting for " + type.getSimpleName());
    }

    private void send(CfdpPacket packet) {
        cfdpIn.emitTuple(packet.toTuple(TimeEncoding.getWallclockTime()));
    }

    private void sendData(CfdpHeader hdr, byte[] data, int start, int end) {
        CfdpHeader dataHdr = hdr.copy(false);
        for (int off = start; off < end; off += 500) {
            int e = Math.min(end, off + 500);
            send(new FileDataPacket(Arrays.copyOfRange(data, off, e), off, dataHdr));
        }
    }

    private void sync() throws Exception {
        for (int i = 0; i < 3; i++) {
            service.getExecutor().submit(() -> {
            }).get();
        }
        service.store.flush();
    }

    private void waitForState(OngoingCfdpTransfer trsf, TransferState state) throws InterruptedException {
        for (int i = 0; i < 100 && trsf.getTransferState() != state; i++) {
            Thread.sleep(50);
        }
        assertEquals(state, trsf.getTransferState(), "failure reason: " + trsf.getFailuredReason());
    }

    private void waitForDeletion(long id) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!Files.exists(transferDir.resolve(id + ".data"))
                    && !Files.exists(transferDir.resolve(id + ".state"))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The files of transfer " + id + " have not been deleted");
    }

    private FileTransfer searchArchive(long id) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Thread.sleep(200);
        var res = ydb.execute("select * from cfdp where id=?", id);
        assertTrue(res.hasNext(), "transfer " + id + " not found in the database");
        var ct = new CompletedTransfer(res.next());
        res.close();
        return ct;
    }

    static String uniqueName(String name) {
        return name + "-" + System.nanoTime();
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
