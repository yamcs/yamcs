package org.yamcs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.yamcs.client.BulkRestDataReceiver;
import org.yamcs.client.ClientException;
import org.yamcs.client.ConnectionListener;
import org.yamcs.client.RestClient;
import org.yamcs.client.WebSocketClient;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.YamcsConnectionProperties;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.Message;

import io.netty.handler.codec.http.HttpMethod;

public abstract class AbstractIntegrationTest {
    protected final String yamcsInstance = "IntegrationTest";
    ParameterProvider parameterProvider;
    MyConnectionListener connectionListener;
    YamcsClient yamcsClient;

    WebSocketClient wsClient;
    RestClient restClient;

    protected String adminUsername = "admin";
    protected char[] adminPassword = "rootpassword".toCharArray();
    RefMdbPacketGenerator packetGenerator; // sends data to tm_realtime
    RefMdbPacketGenerator packetGenerator2; // sends data to tm2_realtime
    static YamcsServer yamcs;

    static {
        // LoggingUtils.enableLogging();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        setupYamcs();
    }

    @Before
    public void before() throws ClientException {
        parameterProvider = ParameterProvider.instance;
        assertNotNull(parameterProvider);

        connectionListener = new MyConnectionListener();
        yamcsClient = YamcsClient.newBuilder("localhost", 9190)
                .withUserAgent("it-junit")
                .build();
        yamcsClient.addConnectionListener(connectionListener);
        if (!yamcs.getSecurityStore().getGuestUser().isActive()) {
            yamcsClient.connect(adminUsername, adminPassword);
        }

        restClient = yamcsClient.getRestClient();
        wsClient = yamcsClient.getWebSocketClient();

        packetGenerator = PacketProvider.instance[0].mdbPacketGenerator;
        packetGenerator.setGenerationTime(TimeEncoding.INVALID_INSTANT);
        packetGenerator2 = PacketProvider.instance[1].mdbPacketGenerator;
        packetGenerator2.setGenerationTime(TimeEncoding.INVALID_INSTANT);

        yamcs.getInstance(yamcsInstance).getProcessor("realtime").getParameterRequestManager().getAlarmServer()
                .clearAll();
    }

    private static void setupYamcs() throws Exception {
        Path dataDir = Paths.get("/tmp/yamcs-IntegrationTest-data");
        FileUtils.deleteRecursivelyIfExists(dataDir);

        YConfiguration.setupTest("IntegrationTest");

        yamcs = YamcsServer.getServer();
        yamcs.prepareStart();
        yamcs.start();
    }

    @After
    public void after() throws InterruptedException {
        yamcsClient.close();
        assertTrue(connectionListener.onDisconnect.tryAcquire(5, TimeUnit.SECONDS));
    }

    @AfterClass
    public static void shutDownYamcs() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    void generatePkt13AndPps(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i = 0; i < numPackets; i++) {
            packetGenerator.setGenerationTime(t0 + 1000 * i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();

            // parameters are 10ms later than packets to make sure that we have a predictable order during replay
            parameterProvider.setGenerationTime(t0 + 1000 * i + 10);
            parameterProvider.generateParameters(i);
        }
    }

    void generatePkt1AndTm2Pkt1(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i = 0; i < numPackets; i++) {
            packetGenerator.setGenerationTime(t0 + 1000 * i);
            packetGenerator.generate_PKT1_1();

            packetGenerator2.setGenerationTime(t0 + 1000 * i);
            packetGenerator2.generate_TM2_PKT1();
        }
    }

    void generatePkt7(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i = 0; i < numPackets; i++) {
            packetGenerator.setGenerationTime(t0 + 1000 * i);
            packetGenerator.generate_PKT7();
        }
    }

    void generatePkt8(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i = 0; i < numPackets; i++) {
            packetGenerator.setGenerationTime(t0 + 1000 * i);
            packetGenerator.generate_PKT8();
        }
    }

    protected <T extends Message> byte[] doRealtimeRequest(String path, HttpMethod method, T msg) throws Exception {
        return restClient.doRequest("/processors/IntegrationTest/realtime" + path, method, msg).get();
    }

    protected RestClient getRestClient(String username, String password) throws ClientException {
        RestClient restClient1 = null;
        try {
            YamcsConnectionProperties yprops = new YamcsConnectionProperties("localhost", 9190);
            restClient1 = new RestClient(yprops);
            restClient1.login(username, password.toCharArray());
            restClient1.setAutoclose(false);
            return restClient1;
        } catch (ClientException e) {
            restClient1.close();
            throw e;
        }
    }

    static class MyConnectionListener implements ConnectionListener {
        Semaphore onConnect = new Semaphore(0);
        Semaphore onDisconnect = new Semaphore(0);

        @Override
        public void connecting(String url) {
        }

        @Override
        public void connected(String url) {
            onConnect.release();
        }

        @Override
        public void connectionFailed(String url, ClientException exception) {
        }

        @Override
        public void disconnected() {
            onDisconnect.release();
        }

        @Override
        public void log(String message) {
        }
    }

    public static class PacketProvider implements TmPacketDataLink {
        static volatile PacketProvider[] instance = new PacketProvider[2];
        RefMdbPacketGenerator mdbPacketGenerator = new RefMdbPacketGenerator();
        YConfiguration config;
        String name;

        public PacketProvider(String yinstance, String name, YConfiguration args) {
            instance[args.getInt("num", 0)] = this;
            this.config = args;
            this.name = name;
        }

        @Override
        public Status getLinkStatus() {
            return Status.OK;
        }

        @Override
        public String getDetailedStatus() {
            return "OK";
        }

        @Override
        public void enable() {
        }

        @Override
        public void disable() {

        }

        @Override
        public boolean isDisabled() {
            return false;
        }

        @Override
        public long getDataInCount() {
            return 0;
        }

        @Override
        public long getDataOutCount() {
            return 0;
        }

        @Override
        public void resetCounters() {
        }

        @Override
        public void setTmSink(TmSink tmSink) {
            mdbPacketGenerator.setTmSink(tmSink);
        }

        @Override
        public YConfiguration getConfig() {
            return config;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static class ParameterProvider implements ParameterDataLink {
        int seqNum = 0;
        ParameterSink ppListener;
        long generationTime;

        static volatile ParameterProvider instance;
        XtceDb xtcedb;
        YConfiguration config;

        String name;

        public ParameterProvider(String yamcsInstance, String name, YConfiguration args) {
            instance = this;
            xtcedb = XtceDbFactory.getInstance(yamcsInstance);
            this.config = args;
            this.name = name;
        }

        @Override
        public Status getLinkStatus() {
            return Status.OK;
        }

        @Override
        public String getDetailedStatus() {
            return null;
        }

        @Override
        public void enable() {
        }

        @Override
        public void disable() {
        }

        @Override
        public boolean isDisabled() {
            return false;
        }

        @Override
        public long getDataInCount() {
            return 0;
        }

        @Override
        public long getDataOutCount() {
            return seqNum * 3;
        }

        @Override
        public void resetCounters() {
        }

        @Override
        public void setParameterSink(ParameterSink ppListener) {
            this.ppListener = ppListener;
        }

        public void setGenerationTime(long genTime) {
            this.generationTime = genTime;
        }

        void generateParameters(int x) {

            ParameterValue pv1 = new ParameterValue(xtcedb.getParameter("/REFMDB/SUBSYS1/processed_para_uint"));
            pv1.setUnsignedIntegerValue(x);
            pv1.setGenerationTime(generationTime);

            ParameterValue pv2 = new ParameterValue(xtcedb.getParameter("/REFMDB/SUBSYS1/processed_para_string"));
            pv2.setGenerationTime(generationTime);
            pv2.setStringValue("para" + x);

            // add a parameter raw value to see that it's calibrated
            ParameterValue pv5 = new ParameterValue(xtcedb.getParameter("/REFMDB/SUBSYS1/processed_para_enum_nc"));
            pv5.setGenerationTime(generationTime);
            pv5.setRawUnsignedInteger(1);

            ppListener.updateParameters(generationTime, "IntegrationTest", seqNum, Arrays.asList(pv1, pv2, pv5));

            // this one should be combined with the two above in the archive as they have the same generation time,
            // group and sequence
            org.yamcs.protobuf.Pvalue.ParameterValue pv3 = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder()
                    .setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                    .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_double"))
                    .setGenerationTime(TimeEncoding.toProtobufTimestamp(generationTime))
                    .setEngValue(ValueUtility.getDoubleGbpValue(x))
                    .build();
            ppListener.updateParams(generationTime, "IntegrationTest", seqNum, Arrays.asList(pv3));

            // mixup some ParameterValue with Protobuf ParameterValue to test compatibility with old yamcs
            org.yamcs.protobuf.Pvalue.ParameterValue pv4 = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder()
                    .setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                    .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_uint"))
                    .setGenerationTime(TimeEncoding.toProtobufTimestamp(generationTime + 20))
                    .setEngValue(ValueUtility.getUint32GbpValue(x))
                    .build();
            ppListener.updateParams(generationTime + 20, "IntegrationTest2", seqNum, Arrays.asList(pv4));

            seqNum++;
        }

        @Override
        public YConfiguration getConfig() {
            return config;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    class MyBulkReceiver implements BulkRestDataReceiver {
        List<byte[]> dist = new ArrayList<>();

        @Override
        public void receiveException(Throwable t) {
            fail(t.getMessage());
        }

        @Override
        public void receiveData(byte[] data) throws ClientException {
            dist.add(data);
        }
    }
}
