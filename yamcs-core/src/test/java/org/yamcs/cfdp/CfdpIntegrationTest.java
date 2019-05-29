package org.yamcs.cfdp;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Cfdp.CreateTransferRequest;
import org.yamcs.protobuf.Cfdp.TransferDirection;
import org.yamcs.protobuf.Cfdp.TransferInfo;
import org.yamcs.utils.FileUtils;
import org.yamcs.web.rest.BucketRestHandler;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
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
    String bucketName = "cfdp-bucket";
    String yamcsInstance = "cfdp-test-inst";
    YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9193, yamcsInstance);

    RestClient restClient;

    @BeforeClass
    public static void beforeClass() throws Exception {
        // enableLogging();

        File dataDir = new File("/tmp/yamcs-cfdp-data");
        FileUtils.deleteRecursively(dataDir.toPath());
        YConfiguration.setup("cfdp");
        YamcsServer.setupYamcsServer();
    }

    @Before
    public void before() throws InterruptedException {
        restClient = new RestClient(ycp);
        restClient.setAcceptMediaType(MediaType.PROTOBUF);
        restClient.setSendMediaType(MediaType.PROTOBUF);
        restClient.setAutoclose(false);
    }

    @Test
    public void testRandomFileUpload() throws Exception {
        byte[] data = createObject("randomfile", 1000);
        uploadAndCheck("randomfile", data);

    }

    private void uploadAndCheck(String objName, byte[] data) throws Exception {
        MyFileReceiver rec = new MyFileReceiver();
        
        CreateTransferRequest ctr = CreateTransferRequest.newBuilder().setBucket(bucketName).setObjectName(objName).setDirection(TransferDirection.UPLOAD).build();
        Future<byte[]> responseFuture = restClient.doRequest("/cfdp/" + yamcsInstance + "/transfers", HttpMethod.POST, ctr.toByteArray());
        TransferInfo tinf = TransferInfo.parseFrom(responseFuture.get());
        System.out.println(tinf);
        assertEquals(data.length, tinf.getTotalSize());

        
        // TransferState ts = fromJson(responseFuture.get(), TransferState.newBuilder()).build();

        /*        Future<String> responseFuture = restClient.doRequest("/cfdp/list", HttpMethod.GET, "");
        
        // Future<String> responseFuture =
        // restClient.doRequest("/cfdp/"+yamcsInstance+"/"+bucketName+"/"+objName+"?target=cfdp-tgt1", HttpMethod.POST,
        // "");
        
        TransferStatus ts = fromJson(responseFuture.get(), TransferStatus.newBuilder()).build();
        int id = 5;
        MyFileReceiver rx = new MyFileReceiver();
        
        // TODO ...check maybe in a loop that the transfer is progressing until finished
        
        while (ts.getSizeTransferred() < ts.getTotalSize()) {
            responseFuture = restClient.doRequest("/cfdp/" + yamcsInstance + "/info?id=" + id, HttpMethod.GET, "");
            ts = fromJson(responseFuture.get(), TransferStatus.newBuilder()).build();
            // TODO assert this assert that
            Thread.sleep(1000);
        }
        
        assertEquals(data, rx.data);
        
        */ // more checks

    }

    // create an object in a bucket
    private byte[] createObject(String objName, int size) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(BucketRestHandler.GLOBAL_INSTANCE);
        BucketDatabase bd = ydb.getBucketDatabase();
        Bucket bucket = bd.createBucket(bucketName);
        byte[] data = new byte[size];
        random.nextBytes(data);
        bucket.putObject(objName, "bla", Collections.emptyMap(), data);

        return data;
    }

    // this should retrieve the file
    class MyFileReceiver {
        byte[] data;

        MyFileReceiver() {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            Stream cfdpIn = ydb.getStream("cfdp_in");
            Stream cfdpOut = ydb.getStream("cfdp_out");
            
            cfdpOut.addSubscriber(new StreamSubscriber() {
                
                @Override
                public void streamClosed(Stream stream) { }
                
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    System.out.println("got tuple "+tuple);
                    
                }
            });

            // TODO start a receiver with those two streams
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
