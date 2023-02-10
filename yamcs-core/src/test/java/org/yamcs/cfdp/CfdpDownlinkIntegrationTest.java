package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.filetransfer.FileTransferClient;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.protobuf.TransferInfo;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.Event;

public class CfdpDownlinkIntegrationTest {
    private Random random = new Random();

    static private String yamcsInstance = "cfdp-test-inst";

    // MyReceiver will place the file in this bucket
    private static Bucket incomingBucket;

    private YamcsClient client;
    private FileTransferClient cfdpClient;

    private YConfiguration config;
    static int seqNum;

    Stream cfdpIn, cfdpOut;
    // executor used by the Cfdp Service
    ScheduledThreadPoolExecutor cfdpServiceExecutor;

    // executor used by the test sender
    ScheduledThreadPoolExecutor myExecutor;

    @BeforeAll
    public static void beforeClass() throws Exception {
        EventProducerFactory.setMockup(true);
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "yamcs-cfdp-data");
        FileUtils.deleteRecursivelyIfExists(dataDir);
        YConfiguration.setupTest("cfdp");
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();

        YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);

        incomingBucket = yarch.getBucket("cfdpDown");
    }

    @AfterAll
    public static void afterClass() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    @BeforeEach
    public void before() throws Exception {
        client = YamcsClient.newBuilder("localhost", 9193).build();
        cfdpClient = new FileTransferClient(client, yamcsInstance, "CfdpService");
        config = getConfig();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        cfdpIn = ydb.getStream("cfdp_in");
        cfdpOut = ydb.getStream("cfdp_out");
        CfdpService cfdpService = YamcsServer.getServer().getService(yamcsInstance, CfdpService.class);
        cfdpService.abortAll();

        ydb.execute("delete from cfdp");
        cfdpServiceExecutor = cfdpService.getExecutor();
        myExecutor = new ScheduledThreadPoolExecutor(1);
        EventProducerFactory.getMockupQueue().clear();
    }

    @AfterEach
    public void after() {
        client.close();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream cfdpOut = ydb.getStream("cfdp_out");
        cfdpOut.getSubscribers().forEach(cfdpOut::removeSubscriber);
    }

    @Test
    public void testClass1() throws Exception {
        byte[] data = new byte[1000];
        random.nextBytes(data);

        downloadAndCheck(config, "randomfile1", data, false, Collections.emptyList(),
                TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testClass2() throws Exception {
        byte[] data = new byte[1000];
        random.nextBytes(data);

        downloadAndCheck(config, "randomfile2", data, true, Collections.emptyList(),
                TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testFileTooLarge1() throws Exception {
        List<Tuple> tlist = new ArrayList<>();
        cfdpOut.addSubscriber((stream, tuple) -> tlist.add(tuple));
        CfdpService cfdpService = YamcsServer.getServer().getService(yamcsInstance, CfdpService.class);
        // allow more time for the finished ack timeout to avoid spurious test errors
        cfdpService.getConfig().getRoot().put("finAckTimeout", Long.valueOf(5000));

        // send a metadata packet with a file larger than max
        CfdpHeader header = new CfdpHeader(true, true, true, false, 2, 3, 15, 12, ++seqNum);
        MetadataPacket mp = new MetadataPacket(false, ChecksumType.MODULAR, 101 * 1024 * 1024, "large-file",
                "large-file", null, header);
        cfdpIn.emitTuple(mp.toTuple(header.getTransactionId(), TimeEncoding.getWallclockTime()));

        synchWithExecutors(1);

        // we expect to receive a Finished PDU indicating file size error
        assertEquals(1, tlist.size());

        FinishedPacket fin = (FinishedPacket) CfdpPacket.fromTuple(tlist.get(0));
        assertEquals(ConditionCode.FILE_SIZE_ERROR, fin.getConditionCode());
        assertEquals(FileStatus.DELIBERATELY_DISCARDED, fin.getFileStatus());

        // in this time the transfer state on the downlink should be cancelling
        TransferInfo tinfo = getTransfer(seqNum);
        assertEquals(TransferState.CANCELLING, tinfo.getState(),
                "Expected CANCELLING, actual state: " + tinfo);

        // send the FIN ACK
        AckPacket ack = new AckPacket(FileDirectiveCode.FINISHED, FileDirectiveSubtypeCode.FINISHED_BY_END_SYSTEM,
                ConditionCode.CANCEL_REQUEST_RECEIVED, TransactionStatus.TERMINATED, header);
        cfdpIn.emitTuple(ack.toTuple(header.getTransactionId(), TimeEncoding.getWallclockTime()));

        synchWithExecutors(1);
        // now the transaction should be finished
        TransferInfo tinfo1 = cfdpClient.getTransfer(tinfo.getId()).get();
        assertEquals(TransferState.FAILED, tinfo1.getState());
        assertTrue(tinfo1.getFailureReason()
                .contains("file size 103424.00 KB exceeding the maximum allowed 102400.00 KB"));

        // restore back the finAckTimeout
        cfdpService.getConfig().getRoot().put("finAckTimeout", Long.valueOf(500));
    }

    @Test
    public void testFileTooLarge2() throws Exception {

        List<Tuple> tlist = new ArrayList<>();
        cfdpOut.addSubscriber((stream, tuple) -> tlist.add(tuple));
        CfdpHeader header = new CfdpHeader(false, true, true, false, 2, 3, 15, 12, ++seqNum);
        FileDataPacket fdp = new FileDataPacket(new byte[] { 0 }, 101 * 1024 * 1024l, header);
        cfdpIn.emitTuple(fdp.toTuple(header.getTransactionId(), TimeEncoding.getWallclockTime()));

        synchWithExecutors(1);
        // we expect to receive a Finished PDU indicating file size error
        assertEquals(1, tlist.size());

        FinishedPacket fin = (FinishedPacket) CfdpPacket.fromTuple(tlist.get(0));
        assertEquals(ConditionCode.FILE_SIZE_ERROR, fin.getConditionCode());
        assertEquals(FileStatus.DELIBERATELY_DISCARDED, fin.getFileStatus());

        TransferInfo tinfo = getTransfer(seqNum);
        assertEquals(TransferState.CANCELLING, tinfo.getState());

        // wait without sending the FIN ACK, should trigger a timeout in the receiver
        Thread.sleep(1000);
        TransferInfo tinfo1 = cfdpClient.getTransfer(tinfo.getId()).get();
        assertTrue(tinfo1.getFailureReason().contains(
                "Received data file whose end offset 105906177 is larger than the maximum file size 104857600"));

        assertTrue(tinfo1.getFailureReason().contains(
                "The Finished PDU has not been acknowledged"));

        assertEquals(TransferState.FAILED, tinfo1.getState());
    }

    @Test
    public void testClass2NoFinAck() throws Exception {
        byte[] data = new byte[1000];
        random.nextBytes(data);

        downloadAndCheck(config, "randomfile3", data, true, Collections.emptyList(),
                TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testClass2EofAckDropped() throws Exception {
        // org.yamcs.LoggingUtils.enableTracing();
        byte[] data = new byte[1000];
        random.nextBytes(data);

        downloadAndCheck(config, "randomfile3", data, true, Arrays.asList(1),
                TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testUnknownLocalEntity() throws Exception {
        CfdpHeader header = new CfdpHeader(true, true, true, false, 2, 3, 15, 13, 5);
        MetadataPacket mp = new MetadataPacket(false, ChecksumType.MODULAR, 1000, "invalid-local-entity",
                "large-file", null, header);
        Tuple t = mp.toTuple(TimeEncoding.getWallclockTime());
        cfdpIn.emitTuple(t);

        verifyEvent("unknown local entity Id 13");
    }

    @Test
    public void testUnknownRemoteEntity() throws Exception {
        CfdpHeader header = new CfdpHeader(true, true, true, false, 2, 3, 16, 12, 5);
        MetadataPacket mp = new MetadataPacket(false, ChecksumType.MODULAR, 1000, "invalid-remote-entity",
                "large-file", null, header);
        Tuple t = mp.toTuple(TimeEncoding.getWallclockTime());
        cfdpIn.emitTuple(t);

        verifyEvent("unknown remote entity Id 16");
    }

    @Test
    public void testLargeFileUnsupported() throws Exception {
        CfdpHeader header = new CfdpHeader(true, true, true, false, 2, 3, 15, 12, 5);
        header.setLargeFile(true);
        MetadataPacket mp = new MetadataPacket(false, ChecksumType.MODULAR, -1, "large-file",
                "large-file", null, header);
        Tuple t = mp.toTuple(TimeEncoding.getWallclockTime());
        cfdpIn.emitTuple(t);

        verifyEvent("Large files not supported");
    }

    @Test
    public void testCorruptedPdu() throws Exception {
        byte[] pdu = new byte[10];
        Tuple t = new Tuple(CfdpPacket.CFDP, Arrays.asList(1, 1, 1, pdu));
        cfdpIn.emitTuple(t);
        verifyEvent("Error decoding CFDP PDU");
    }

    @Test
    public void testUnexpectedPdu() throws Exception {
        CfdpHeader header = new CfdpHeader(true, true, true, false, 2, 3, 15, 12, 5);
        header.setLargeFile(true);
        AckPacket ack = new AckPacket(FileDirectiveCode.ACK, FileDirectiveSubtypeCode.FINISHED_BY_END_SYSTEM,
                ConditionCode.CANCEL_REQUEST_RECEIVED, TransactionStatus.TERMINATED, header);
        cfdpIn.emitTuple(ack.toTuple(TimeEncoding.getWallclockTime()));

        verifyEvent("Unexpected CFDP PDU received");
    }

    @Test
    public void testMaxDownlinkLimit() throws Exception {
        for (int i = 0; i < 101; i++) {
            startDownlink(++seqNum);
        }

        verifyEvent("Maximum number of pending downloads 100 reached.");
        EventProducerFactory.getMockupQueue().clear();

        List<TransferInfo> l = cfdpClient.listTransfers().get();
        assertEquals(100, l.size());

        for (TransferInfo tinfo : l) {
            cfdpClient.cancel(tinfo.getId()).get();
        }

        l = cfdpClient.listTransfers().get();
        for (TransferInfo tinfo : l) {
            assertEquals(TransferState.CANCELLING, tinfo.getState());
        }
        Thread.sleep(1000);

        l = cfdpClient.listTransfers().get();
        for (TransferInfo tinfo : l) {
            assertEquals(TransferState.FAILED, tinfo.getState());
        }
    }

    private void startDownlink(int seqNum) {
        CfdpHeader header = new CfdpHeader(true, true, true, false, 2, 3, 15, 12, seqNum);
        String name = "limit-test" + seqNum;
        MetadataPacket mp = new MetadataPacket(false, ChecksumType.MODULAR, 1000, name, name, null, header);
        cfdpIn.emitTuple(mp.toTuple(TimeEncoding.getWallclockTime()));
    }

    private void downloadAndCheck(YConfiguration config, String objName, byte[] data, boolean reliable,
            List<Integer> dropPackets, TransferState expectedSenderState, TransferState expectedReceiverState)
            throws Exception {

        MyFileSender sender = new MyFileSender(++seqNum, objName, data, dropPackets, config, reliable);

        Thread.sleep(1000);
        TransferInfo tinfo = getTransfer(seqNum);

        assertEquals(expectedSenderState, tinfo.getState());
        assertEquals(expectedReceiverState, sender.trsf.getTransferState());

        if (expectedReceiverState == TransferState.COMPLETED) {
            byte[] recdata = incomingBucket.getObject(tinfo.getObjectName());
            assertArrayEquals(data, recdata);
        }
        assertFalse(sender.trsf.eofTimer.isActive());
    }

    TransferInfo getTransfer(int seqNum) throws InterruptedException, ExecutionException {
        return cfdpClient.listTransfers().get().stream()
                .filter(ti -> ti.getTransactionId().getSequenceNumber() == seqNum)
                .findAny().get();
    }

    // this is the configuration of the sender
    // the receiver is in the src/test/resources/cfdp/yamcs.cfdp-test-inst.yaml
    private YConfiguration getConfig() {
        Map<String, Object> m = new HashMap<>();
        m.put("inactivityTimeout", 10000);

        m.put("sequenceNrLength", 2);
        m.put("entityIdLength", 2);
        m.put("sleepBetweenPdus", 10);

        return YConfiguration.wrap(m);
    }

    private void verifyEvent(String evs) {
        boolean found = false;

        for (Event ev : EventProducerFactory.getMockupQueue()) {
            if (ev.getMessage().contains(evs)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    // allows the CFDP service to process all its incoming queue
    private void synchWithExecutors(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            cfdpServiceExecutor.submit(() -> {
            }).get();

            myExecutor.submit(() -> {
            }).get();
        }
    }

    class MyFileSender implements TransferMonitor {
        CfdpOutgoingTransfer trsf;
        int tcount = 0;
        final List<Integer> dropPackets;

        MyFileSender(int seqNum, String objName, byte[] data, List<Integer> dropPackets, YConfiguration config,
                boolean reliable) {

            this.dropPackets = dropPackets;
            EventProducer eventProducer = EventProducerFactory.getEventProducer();
            eventProducer.setSource("unit-test");
            FilePutRequest putRequest = new FilePutRequest(15, 12, objName, objName, false, reliable, false, false,
                    incomingBucket, data);

            trsf = new CfdpOutgoingTransfer(yamcsInstance, putRequest.getSourceId(), seqNum, TimeEncoding.getWallclockTime(),
                    myExecutor, putRequest, cfdpIn, config, null, null, null, eventProducer, this, null);

            cfdpOut.addSubscriber((stream, tuple) -> {
                tcount++;
                CfdpPacket packet = CfdpPacket.fromTuple(tuple);
                // System.out.println("packet" + tcount + ": " + packet);
                if (dropPackets.contains(tcount)) {
                    // System.out.println("dropping packet" + tcount);
                    return;
                }

                // System.out.println("processing packet "+packet);
                trsf.processPacket(packet);
            });
            trsf.start();
        }

        @Override
        public void stateChanged(FileTransfer cfdpTransfer) {
        }
    }
}
