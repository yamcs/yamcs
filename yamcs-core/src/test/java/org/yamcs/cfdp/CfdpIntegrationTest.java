package org.yamcs.cfdp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.client.RestClient;
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

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

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
    }

    @Before
    public void before() throws InterruptedException {
        restClient = new RestClient(ycp);
        restClient.setAcceptMediaType(MediaType.PROTOBUF);
        restClient.setSendMediaType(MediaType.PROTOBUF);
        restClient.setAutoclose(false);
    }

    @Test
    public void testRandomFileUploadClass1() throws Exception {
        byte[] data = createObject("randomfile1", 1000);
        uploadAndCheck("randomfile1", data, false);
    }

    @Test
    public void testRandomFileUploadClass2() throws Exception {
        byte[] data = createObject("randomfile2", 1000);
        uploadAndCheck("randomfile2", data, true);
    }

    private void uploadAndCheck(String objName, byte[] data, boolean reliable) throws Exception {
        MyFileReceiver rec = new MyFileReceiver();

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
            responseFuture = restClient.doRequest("/cfdp/" + yamcsInstance + "/transfers/" + tinf.getTransactionId(),
                    HttpMethod.GET);
            tinfo1 = TransferInfo.parseFrom(responseFuture.get());

            if (tinfo1.getState() == TransferState.COMPLETED) {
                break;
            }
        }
        assertNotNull(tinfo1);
        assertEquals(TransferState.COMPLETED, tinfo1.getState());

        byte[] recdata = incomingBucket.getObject(rec.trsf.getObjectName());
        assertArrayEquals(data, recdata);

    }

    // create an object in a bucket
    private byte[] createObject(String objName, int size) throws Exception {
        byte[] data = new byte[size];
        random.nextBytes(data);
        outgoingBucket.putObject(objName, "bla", Collections.emptyMap(), data);

        return data;
    }

    // this should retrieve the file
    class MyFileReceiver {
        byte[] data;
        CfdpIncomingTransfer trsf;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

        MyFileReceiver() {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            Stream cfdpIn = ydb.getStream("cfdp_in");
            Stream cfdpOut = ydb.getStream("cfdp_out");

            cfdpOut.addSubscriber(new StreamSubscriber() {
                @Override
                public void streamClosed(Stream stream) {
                }

                @Override
                public void onTuple(Stream stream, Tuple tuple) {

                    CfdpPacket packet = CfdpPacket.fromTuple(tuple);
                    if (trsf == null) {
                        MetadataPacket mdp = (MetadataPacket) packet;
                        trsf = new CfdpIncomingTransfer(executor, (MetadataPacket) mdp, cfdpIn, incomingBucket, null);
                    } else {
                        trsf.processPacket(packet);
                    }
                }
            });
        }
    }

    protected static String getYamcsInstance() {
        return "cfdp";
    }

    <T extends Message.Builder> T fromJson(String json, T builder) throws IOException {
        JsonFormat.parser().merge(json, builder);
        return builder;
    }
}
