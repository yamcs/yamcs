package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.client.ClientException;
import org.yamcs.client.ConnectionListener;
import org.yamcs.client.YamcsClient;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.mdb.Mdb;

public abstract class AbstractIntegrationTest {

    protected final String yamcsHost = "localhost";
    protected final int yamcsPort = 9190;
    protected final String yamcsInstance = "instance1";

    ParameterProvider parameterProvider;
    MyConnectionListener connectionListener;
    protected YamcsClient yamcsClient;

    protected String adminUsername = "admin";
    protected char[] adminPassword = "rootpassword".toCharArray();
    RefMdbPacketGenerator packetGenerator; // sends data to tm_realtime
    RefMdbPacketGenerator packetGenerator2; // sends data to tm2_realtime
    static YamcsServer yamcs;

    static {
        // LoggingUtils.enableLogging();
    }

    @BeforeAll
    public static void beforeClass() throws Exception {
        setupYamcs("IntegrationTest", true);
    }

    @BeforeEach
    public void before() throws ClientException {
        parameterProvider = ParameterProvider.instance[0];
        assertNotNull(parameterProvider);

        connectionListener = new MyConnectionListener();
        yamcsClient = YamcsClient.newBuilder(yamcsHost, yamcsPort)
                .withUserAgent("it-junit")
                .build();
        yamcsClient.addConnectionListener(connectionListener);
        if (yamcs.getSecurityStore().isEnabled()) {
            yamcsClient.login(adminUsername, adminPassword);
        }
        yamcsClient.connectWebSocket();

        packetGenerator = PacketProvider.instance[0].mdbPacketGenerator;
        packetGenerator.setGenerationTime(TimeEncoding.INVALID_INSTANT);
        packetGenerator2 = PacketProvider.instance[1].mdbPacketGenerator;
        packetGenerator2.setGenerationTime(TimeEncoding.INVALID_INSTANT);

        yamcs.getInstance(yamcsInstance).getProcessor("realtime").getParameterProcessorManager().getAlarmServer()
                .clearAll();
    }

    protected static void setupYamcs(String integrationTestConfig, boolean cleanExistingData) throws Exception {
        if (cleanExistingData) {
            Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "yamcs-IntegrationTest-data");
            FileUtils.deleteRecursivelyIfExists(dataDir);
        }
        YConfiguration.setupTest(integrationTestConfig);

        yamcs = YamcsServer.getServer();
        yamcs.prepareStart();
        yamcs.start();
    }

    @AfterEach
    public void after() throws InterruptedException {
        yamcsClient.close();
        assertTrue(connectionListener.onDisconnect.tryAcquire(5, TimeUnit.SECONDS));
    }

    @AfterAll
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

    void generatePkt3(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i = 0; i < numPackets; i++) {
            packetGenerator.setGenerationTime(t0 + 1000 * i);
            packetGenerator.generate_PKT3();
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

    void generatePkt13AndTm2Pkt1(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i = 0; i < numPackets; i++) {
            packetGenerator.setGenerationTime(t0 + 1000 * i);
            packetGenerator.generate_PKT13();

            packetGenerator2.setGenerationTime(t0 + 1000 * i);
            packetGenerator2.generate_TM2_PKT1();
        }
    }

    static class MyConnectionListener implements ConnectionListener {
        Semaphore onConnect = new Semaphore(0);
        Semaphore onDisconnect = new Semaphore(0);

        @Override
        public void connecting() {
        }

        @Override
        public void connected() {
            onConnect.release();
        }

        @Override
        public void connectionFailed(Throwable cause) {
        }

        @Override
        public void disconnected() {
            onDisconnect.release();
        }
    }

    public static class PacketProvider implements TmPacketDataLink {
        static volatile PacketProvider[] instance = new PacketProvider[4];
        RefMdbPacketGenerator mdbPacketGenerator = new RefMdbPacketGenerator();
        YConfiguration config;
        String name;

        @Override
        public void init(String yamcsInstance, String linkName, YConfiguration config) {
            instance[config.getInt("num", 0)] = this;
            this.config = config;
            this.name = linkName;
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

        static volatile ParameterProvider[] instance = new ParameterProvider[2];
        Mdb xtcedb;
        YConfiguration config;

        String name;

        @Override
        public void init(String yamcsInstance, String linkName, YConfiguration config) {
            instance[config.getInt("num", 0)] = this;
            xtcedb = MdbFactory.getInstance(yamcsInstance);
            this.config = config;
            this.name = linkName;
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

    public static class TcDataLink extends AbstractTcDataLink {
        static short seqNum = 5000;
        static volatile TcDataLink[] instance = new TcDataLink[4];

        List<PreparedCommand> commands = new ArrayList<>();

        @Override
        public void init(String yamcsInstance, String name, YConfiguration config) {
            super.init(yamcsInstance, name, config);
            instance[config.getInt("num", 0)] = this;
        }

        @Override
        public boolean sendCommand(PreparedCommand preparedCommand) {
            if (preparedCommand.getCmdName().contains("ALG_VERIF_TC")) {
                commandHistoryPublisher.publish(preparedCommand.getCommandId(), "packetSeqNum", seqNum);
            }
            commands.add(preparedCommand);
            return true;
        }

        @Override
        protected Status connectionStatus() {
            return Status.OK;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }
    }
}
