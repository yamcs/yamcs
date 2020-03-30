package org.yamcs.cfdp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.client.RestClient;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest.UploadOptions;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferInfo;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import io.netty.handler.codec.http.HttpMethod;

public class CfdpIntegrationTest {
    Random random = new Random();

    String yamcsInstance = "cfdp-test-inst";

    // files to be sent are created here
    static Bucket outgoingBucket;

    // MyReceiver will place the file in this bucket
    static Bucket incomingBucket;

    YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9193, yamcsInstance);

    RestClient restClient;

    @BeforeClass
    public static void beforeClass() throws Exception {
        EventProducerFactory.setMockup(false);
        Path dataDir = Paths.get("/tmp/yamcs-cfdp-data");
        FileUtils.deleteRecursivelyIfExists(dataDir);
        YConfiguration.setupTest("cfdp");
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();

        YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        incomingBucket = yarch.createBucket("cfdp-bucket-in");
        outgoingBucket = yarch.createBucket("cfdp-bucket-out");
        // LoggingUtils.enableLogging();
    }

    @Before
    public void before() throws InterruptedException {
        restClient = new RestClient(ycp);
        restClient.setAutoclose(false);
    }

    @Before
    public void after() throws InterruptedException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream cfdpOut = ydb.getStream("cfdp_out");
        cfdpOut.getSubscribers().forEach(s -> cfdpOut.removeSubscriber(s));
    }

    @Test
    public void testClass1() throws Exception {
        byte[] data = createObject("randomfile1", 1000);
        uploadAndCheck("randomfile1", data, false, Collections.emptyList(), TransferState.COMPLETED,
                TransferState.COMPLETED);
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
        // this will lose the first data packet
        uploadAndCheck("randomfile3", data, false, Arrays.asList(2), TransferState.COMPLETED, TransferState.FAILED);
    }

    @Test
    public void testClass1WithPacketLoss2() throws Exception {
        byte[] data = createObject("randomfile4", 1000);
        // this will lose the first data packet and the EOF
        uploadAndCheck("randomfile4", data, false, Arrays.asList(2, 5), TransferState.COMPLETED, TransferState.FAILED);
    }

    @Test
    public void testClass2WithPacketLoss() throws Exception {
        byte[] data = createObject("randomfile5", 1000);
        // this will lose the first data packet
        uploadAndCheck("randomfile5", data, true, Arrays.asList(2), TransferState.COMPLETED, TransferState.COMPLETED);
    }

    @Test
    public void testClass2WithPacketLoss2() throws Exception {
        byte[] data = createObject("randomfile5", 1000);
        // this will lose the first data packet and the first EOF
        uploadAndCheck("randomfile5", data, true, Arrays.asList(2, 5), TransferState.COMPLETED,
                TransferState.COMPLETED);
    }

    @Test
    public void testClass2WithPacketLoss3() throws Exception {
        byte[] data = createObject("randomfile6", 1000);
        // this will lose the first data packet and all the EOF
        uploadAndCheck("randomfile6", data, true, Arrays.asList(2, 5, 6), TransferState.FAILED, TransferState.FAILED);
    }

    private void uploadAndCheck(String objName, byte[] data, boolean reliable, List<Integer> dropPackets,
            TransferState expectedSenderState, TransferState expectedReceiverState) throws Exception {
        MyFileReceiver rec = new MyFileReceiver(dropPackets);

        CreateTransferRequest ctr = CreateTransferRequest.newBuilder().setBucket(outgoingBucket.getName())
                .setObjectName(objName)
                .setDirection(TransferDirection.UPLOAD)
                .setUploadOptions(UploadOptions.newBuilder().setReliable(reliable).build())
                .build();
        Future<byte[]> responseFuture = restClient.doRequest("/cfdp/" + yamcsInstance + "/transfers", HttpMethod.POST,
                ctr.toByteArray());
        TransferInfo tinf = TransferInfo.parseFrom(responseFuture.get());
        assertEquals(data.length, tinf.getTotalSize());
        assertEquals(TransferState.RUNNING, tinf.getState());
        assertEquals(reliable, tinf.getReliable());
        TransferInfo tinfo1 = null;

        for (int i = 0; i < 3; i++) {
            Thread.sleep(1000);
            responseFuture = restClient.doRequest("/cfdp/" + yamcsInstance + "/transfers/" + tinf.getId(),
                    HttpMethod.GET);
            tinfo1 = TransferInfo.parseFrom(responseFuture.get());
            if (isFinished(tinfo1.getState()) && isFinished(rec.trsf.getTransferState())) {
                break;
            }
        }
        assertNotNull(tinfo1);
        assertEquals(expectedSenderState, tinfo1.getState());
        assertEquals(expectedReceiverState, rec.trsf.getTransferState());

        if (expectedReceiverState == TransferState.COMPLETED) {
            byte[] recdata = incomingBucket.getObject(rec.trsf.getObjectName());
            assertArrayEquals(data, recdata);
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
        m.put("inactivityTimeout", 1000);
        return YConfiguration.wrap(m);
    }

    // this should retrieve the file
    class MyFileReceiver {
        byte[] data;
        CfdpIncomingTransfer trsf;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        int tcount = 0;
        final List<Integer> dropPackets;

        MyFileReceiver(List<Integer> dropPackets) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            Stream cfdpIn = ydb.getStream("cfdp_in");
            Stream cfdpOut = ydb.getStream("cfdp_out");
            this.dropPackets = dropPackets;

            cfdpOut.addSubscriber(new StreamSubscriber() {
                @Override
                public void streamClosed(Stream stream) {
                }

                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    tcount++;
                    CfdpPacket packet = CfdpPacket.fromTuple(tuple);
                    // System.out.println("packet"+tcount+": "+packet);
                    if (dropPackets.contains(tcount)) {
                        // System.out.println("dropping packet"+tcount);
                        return;
                    }

                    if (trsf == null) {
                        MetadataPacket mdp = (MetadataPacket) packet;
                        trsf = new CfdpIncomingTransfer("test", executor, getConfig(), (MetadataPacket) mdp, cfdpIn,
                                incomingBucket,
                                null);
                    } else {
                        trsf.processPacket(packet);
                    }
                }
            });
        }
    }
}
