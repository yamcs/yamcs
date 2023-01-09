package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
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
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.client.ClientException;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.filetransfer.FileTransferClient;
import org.yamcs.client.filetransfer.FileTransferClient.UploadOptions;
import org.yamcs.client.storage.ObjectId;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.protobuf.EntityInfo;
import org.yamcs.protobuf.FileTransferServiceInfo;
import org.yamcs.protobuf.TransferInfo;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class CfdpUplinkIntegrationTest {
    private Random random = new Random();

    private String yamcsInstance = "cfdp-test-inst";

    // files to be sent are created here
    private static Bucket outgoingBucket;

    // MyReceiver will place the file in this bucket
    private static Bucket incomingBucket;

    private YamcsClient client;
    private FileTransferClient cfdpClient;

    private YConfiguration config;

    @BeforeAll
    public static void beforeClass() throws Exception {
        EventProducerFactory.setMockup(false);
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "yamcs-cfdp-data");
        FileUtils.deleteRecursivelyIfExists(dataDir);
        YConfiguration.setupTest("cfdp");
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();

        YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);

        incomingBucket = yarch.createBucket("cfdp-bucket-in");
        outgoingBucket = yarch.createBucket("cfdp-bucket-out");
    }

    @AfterAll
    public static void afterClass() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    @BeforeEach
    public void before() {
        client = YamcsClient.newBuilder("localhost", 9193).build();
        cfdpClient = new FileTransferClient(client, yamcsInstance, "CfdpService");
        config = getConfig();
    }

    @AfterEach
    public void after() {
        client.close();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream cfdpOut = ydb.getStream("cfdp_out");
        cfdpOut.getSubscribers().forEach(cfdpOut::removeSubscriber);
    }

    @Test
    public void testGetServices() throws Exception {
        List<FileTransferServiceInfo> l = client.getFileTransferServices(yamcsInstance).get();
        assertEquals(1, l.size());
        FileTransferServiceInfo ftInfo = l.get(0);
        assertEquals("CfdpService", ftInfo.getName());
        assertEquals(1, ftInfo.getLocalEntitiesCount());
        assertEquals(1, ftInfo.getRemoteEntitiesCount());
        checkEquals("local12", 12, ftInfo.getLocalEntities(0));
        checkEquals("remote15", 15, ftInfo.getRemoteEntities(0));

    }

    @Test
    public void testInvalidId() throws Exception {
        ClientException ce = null;
        try {
            int invalidId = 1234567;
            cfdpClient.getTransfer(invalidId).get();
        } catch (ExecutionException e) {
            ce = (ClientException) e.getCause();
        }

        assertNotNull(ce);
        assertTrue(ce.getMessage().contains("No such transaction"));
    }

    private void checkEquals(String name, int id, EntityInfo einfo) {
        assertEquals(name, einfo.getName());
        assertEquals(id, einfo.getId());
    }

    @Test
    public void testClass1() throws Exception {
        byte[] data = createObject("randomfile1", 1000);
        uploadAndCheck("randomfile1", data, false, Collections.emptyList(),
                TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testClass2() throws Exception {
        byte[] data = createObject("randomfile2", 1000);
        uploadAndCheck("randomfile2", data, true, Collections.emptyList(), TransferState.COMPLETED,
                TransferState.COMPLETED);
    }

    @Test
    public void testClass1WithPacketLoss() throws Exception {
        byte[] data = createObject("randomfile3", 1000);

        YConfiguration rcvConfig = config;
        rcvConfig.getRoot().put("inactivityTimeout", 10000);
        rcvConfig.getRoot().put("checkAckTimeout", 1000);
        rcvConfig.getRoot().put("checkAckLimit", 1);

        // this will lose the first data packet
        uploadAndCheck(rcvConfig, "randomfile3", data, false, Arrays.asList(2), TransferState.COMPLETED,
                TransferState.FAILED);

    }

    @Test
    public void testClass1WithPacketLoss2() throws Exception {
        byte[] data = createObject("randomfile4", 1000);

        config.getRoot().put("inactivityTimeout", 1000);

        // this will lose the first data packet and the EOF
        uploadAndCheck(config, "randomfile4", data, false, Arrays.asList(2, 5), TransferState.COMPLETED,
                TransferState.FAILED);
    }

    @Test
    public void testClass2WithPacketLoss() throws Exception {
        byte[] data = createObject("randomfile51", 1000);
        // this will lose the first data packet
        uploadAndCheck("randomfile51", data, true, Arrays.asList(2), TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testClass2WithPacketLoss2() throws Exception {
        byte[] data = createObject("randomfile52", 1000);
        // this will lose the first data packet and the first EOF
        config.getRoot().put("immediateNak", false);

        uploadAndCheck(config, "randomfile52", data, true, Arrays.asList(2, 5), TransferState.COMPLETED,
                TransferState.COMPLETED);
    }

    @Test
    public void testClass2WithPacketLoss3() throws Exception {
        byte[] data = createObject("randomfile6", 1000);
        config.getRoot().put("inactivityTimeout", 1000);

        // this will lose the the 2 EOF
        uploadAndCheck(config, "randomfile6", data, true, Arrays.asList(5, 6), TransferState.FAILED,
                TransferState.FAILED);
    }

    @Test
    public void testClass2WithMetadtaLoss() throws Exception {
        byte[] data = createObject("randomfile71", 1000);
        uploadAndCheck("randomfile71", data, true, Arrays.asList(1), TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testClass2WithMetadtaAfterEof() throws Exception {
        byte[] data = createObject("randomfile72", 1000);
        config.getRoot().put("immediateNak", false);
        uploadAndCheck(config, "randomfile72", data, true, Arrays.asList(1), TransferState.COMPLETED,
                TransferState.COMPLETED);
    }

    @Test
    public void testClass2WithDeadReceiver() throws Exception {
        byte[] data = createObject("randomfile72", 1000);
        uploadAndCheck(config, "randomfile72", data, true, Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7),
                TransferState.FAILED, null);
    }

    @Test
    public void testClass2WithLostFinAck() throws Exception {
        byte[] data = createObject("randomfile72", 1000);
        uploadAndCheck(config, "randomfile72", data, true, Arrays.asList(6, 7, 8),
                TransferState.COMPLETED, TransferState.FAILED);
    }

    @Test
    public void testClass2WithNackLimitReached() throws Exception {
        byte[] data = createObject("randomfile81", 1000);
        // lose the first data packet, and then all retransmissions
        config.getRoot().put("immediateNak", false);
        config.getRoot().put("nakTimeout", 500);
        config.getRoot().put("nakLimit", 2);

        TransferInfo tinfo = uploadAndCheck("randomfile81", data, true, Arrays.asList(2, 6, 7, 8),
                TransferState.FAILED, TransferState.FAILED);

        assertTrue(tinfo.getFailureReason().contains("NAK_LIMIT_REACHED"));
    }

    @Test
    public void testPauseResume() throws Exception {

        // patch the sleepBetweenPdus such that the transfer does not finish immediately
        CfdpService cfdpService = YamcsServer.getServer().getServices(yamcsInstance, CfdpService.class).get(0);
        cfdpService.getConfig().getRoot().put("sleepBetweenPdu", Long.valueOf(500));

        // start receiver
        MyFileReceiver receiver = new MyFileReceiver(Collections.emptyList(), config);

        // create object
        String objName = "randomfile23-01-2022";
        byte[] data = createObject(objName, 1000);
        ObjectId object = ObjectId.of(outgoingBucket.getName(), objName);

        // initiate transfer
        TransferInfo tinfo = cfdpClient.upload(object, UploadOptions.reliable(true)).get();
        assertEquals(data.length, tinfo.getTotalSize());

        cfdpClient.pause(tinfo.getId()).get();
        int tCount = receiver.tcount;

        TransferInfo tinfo1 = cfdpClient.getTransfer(tinfo.getId()).get();
        assertEquals(TransferState.PAUSED, tinfo1.getState());

        receiver.suspend();

        Thread.sleep(1000);
        assertEquals(tCount, receiver.tcount);

        cfdpClient.resume(tinfo.getId()).get();

        receiver.resume();

        TransferInfo tinfo2 = cfdpClient.getTransfer(tinfo.getId()).get();
        assertEquals(TransferState.RUNNING, tinfo2.getState());

        waitTransferFinished(receiver, tinfo.getId());

        byte[] recdata = incomingBucket.getObject(receiver.trsf.getObjectName());
        assertArrayEquals(data, recdata);

        // restore back the old value (otherwise the tests following this will be slow and even fail)
        cfdpService.getConfig().getRoot().put("sleepBetweenPdu", Long.valueOf(10));
    }

    @Test
    public void testAutoPause() throws Exception {
        // TODO
    }

    private TransferInfo uploadAndCheck(String objName, byte[] data, boolean reliable, List<Integer> dropPackets,
            TransferState expectedSenderState, TransferState expectedReceiverState) throws Exception {
        return uploadAndCheck(config, objName, data, reliable, dropPackets, expectedSenderState, expectedReceiverState);

    }

    private TransferInfo uploadAndCheck(YConfiguration config, String objName, byte[] data, boolean reliable,
            List<Integer> dropPackets, TransferState expectedSenderState, TransferState expectedReceiverState)
            throws Exception {
        MyFileReceiver rec = new MyFileReceiver(dropPackets, config);

        ObjectId object = ObjectId.of(outgoingBucket.getName(), objName);
        TransferInfo tinf = cfdpClient.upload(object, UploadOptions.reliable(reliable)).get();
        assertEquals(data.length, tinf.getTotalSize());
        assertEquals(TransferState.RUNNING, tinf.getState());
        assertEquals(reliable, tinf.getReliable());

        waitTransferFinished(rec, tinf.getId());

        TransferInfo tinfo1 = cfdpClient.getTransfer(tinf.getId()).get();
        assertEquals(expectedSenderState, tinfo1.getState());
        if (expectedReceiverState != null) {
            assertEquals(expectedReceiverState, rec.trsf.getTransferState());

            if (expectedReceiverState == TransferState.COMPLETED) {
                byte[] recdata = incomingBucket.getObject(rec.trsf.getObjectName());
                assertArrayEquals(data, recdata);
            }
        }
        return tinfo1;
    }

    private void waitTransferFinished(MyFileReceiver rec, long id) throws InterruptedException, ExecutionException {
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
            TransferInfo tinfo1 = cfdpClient.getTransfer(id).get();

            if (isFinished(tinfo1.getState()) && (rec.trsf == null || isFinished(rec.trsf.getTransferState()))) {
                break;
            }
        }
    }

    private boolean isFinished(TransferState state) {
        return state == TransferState.COMPLETED || state == TransferState.FAILED;
    }

    // create an object in a bucket
    private byte[] createObject(String objName, int size) throws Exception {
        byte[] data = new byte[size];
        random.nextBytes(data);
        outgoingBucket.putObject(objName, "bla", Collections.emptyMap(), data);

        return data;
    }

    private YConfiguration getConfig() {
        Map<String, Object> m = new HashMap<>();
        m.put("inactivityTimeout", 10000);

        m.put("finAckTimeout", 50);
        m.put("finAckLimit", 2);
        m.put("sleepBetweenPdus", 10);
        m.put("directoryListingFileTemplate", ".dirlist.tmp");

        return YConfiguration.wrap(m);
    }

    // this should retrieve the file
    class MyFileReceiver implements TransferMonitor {
        byte[] data;
        CfdpIncomingTransfer trsf;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        int tcount = 0;
        final List<Integer> dropPackets;

        MyFileReceiver(List<Integer> dropPackets, YConfiguration config) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            Stream cfdpIn = ydb.getStream("cfdp_in");
            Stream cfdpOut = ydb.getStream("cfdp_out");
            this.dropPackets = dropPackets;
            EventProducer eventProducer = EventProducerFactory.getEventProducer();
            eventProducer.setSource("unit-test");

            cfdpOut.addSubscriber(new StreamSubscriber() {
                @Override
                public void streamClosed(Stream stream) {
                }

                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    tcount++;
                    CfdpPacket packet = CfdpPacket.fromTuple(tuple);
                    // System.out.println("packet" + tcount + ": " + packet);
                    if (dropPackets.contains(tcount)) {
                        // System.out.println("dropping packet" + tcount);
                        return;
                    }

                    if (trsf == null) {
                        FileSaveHandler fileSaveHandler = new FileSaveHandler(yamcsInstance, incomingBucket,
                                null, false,
                                false, false, 1000);

                        trsf = new CfdpIncomingTransfer("test", 1, TimeEncoding.getWallclockTime(), executor, config,
                                packet.getHeader(), cfdpIn, fileSaveHandler, eventProducer, MyFileReceiver.this,
                                Collections.emptyMap());
                    }
                    // System.out.println("processing packet "+packet);
                    trsf.processPacket(packet);
                }
            });
        }

        public void suspend() {
            trsf.suspend();
        }

        public void resume() {
            trsf.resume();
        }

        @Override
        public void stateChanged(FileTransfer cfdpTransfer) {
        }
    }
}
