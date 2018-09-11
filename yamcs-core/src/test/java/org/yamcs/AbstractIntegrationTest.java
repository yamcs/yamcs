package org.yamcs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Web.ConnectionInfo;
import org.yamcs.protobuf.Web.ParameterSubscriptionRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.LinkEvent;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.security.SecurityStore;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.web.HttpServer;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

public abstract class AbstractIntegrationTest {
    final String yamcsInstance = "IntegrationTest";
    PacketProvider packetProvider;
    ParameterProvider parameterProvider;
    YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190, "IntegrationTest");
    MyWsListener wsListener;
    WebSocketClient wsClient;
    RestClient restClient;

    protected String adminUsername = "admin";
    protected char[] adminPassword = "rootpassword".toCharArray();
    RefMdbPacketGenerator packetGenerator;
    static {
        // LoggingUtils.enableLogging();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        setupYamcs();
    }

    @Before
    public void before() throws InterruptedException {

        if (SecurityStore.getInstance().isEnabled()) {
            ycp.setCredentials(adminUsername, adminPassword);
        }

        packetProvider = PacketProvider.instance;
        parameterProvider = ParameterProvider.instance;
        assertNotNull(packetProvider);
        assertNotNull(parameterProvider);

        wsListener = new MyWsListener();

        wsClient = new WebSocketClient(ycp, wsListener);
        wsClient.setUserAgent("it-junit");
        wsClient.connect();
        assertTrue(wsListener.onConnect.tryAcquire(5, TimeUnit.SECONDS));
        restClient = new RestClient(ycp);
        restClient.setAcceptMediaType(MediaType.JSON);
        restClient.setSendMediaType(MediaType.JSON);
        restClient.setAutoclose(false);
        packetGenerator = packetProvider.mdbPacketGenerator;
        packetGenerator.setGenerationTime(TimeEncoding.INVALID_INSTANT);
    }

    private static void setupYamcs() throws Exception {
        File dataDir = new File("/tmp/yamcs-IntegrationTest-data");

        FileUtils.deleteRecursively(dataDir.toPath());

        YConfiguration.setup("IntegrationTest");
        Map<String, Object> options = new HashMap<>();
        options.put("webRoot", "/tmp/yamcs-web");
        options.put("port", 9190);
        new HttpServer(options).startServer();
        // artemisServer = ArtemisServer.setupArtemis();
        // ArtemisManagement.setupYamcsServerControl();
        YamcsServer.setupYamcsServer();
    }

    IssueCommandRequest getCommand(int seq, String... args) {
        IssueCommandRequest.Builder b = IssueCommandRequest.newBuilder();
        b.setOrigin("IntegrationTest");
        b.setSequenceNumber(seq);
        for (int i = 0; i < args.length; i += 2) {
            b.addAssignment(IssueCommandRequest.Assignment.newBuilder().setName(args[i]).setValue(args[i + 1]).build());
        }

        return b.build();
    }

    protected ParameterSubscriptionRequest getSubscription(String... pfqname) {
        return getSubscription(true, false, pfqname);
    }

    protected ParameterSubscriptionRequest getSubscription(boolean sendFromCache, boolean updateOnExpiration,
            String... pfqname) {
        ParameterSubscriptionRequest.Builder b = ParameterSubscriptionRequest.newBuilder();
        for (String p : pfqname) {
            b.addId(NamedObjectId.newBuilder().setName(p).build());
        }
        b.setSendFromCache(sendFromCache);
        b.setUpdateOnExpiration(updateOnExpiration);
        b.setAbortOnInvalid(false);
        return b.build();
    }

    @After
    public void after() throws InterruptedException {
        wsClient.disconnect();
        assertTrue(wsListener.onDisconnect.tryAcquire(5, TimeUnit.SECONDS));
    }

    @AfterClass
    public static void shutDownYamcs() throws Exception {
        YamcsServer.shutDown();
    }

    <T extends Message> String toJson(T msg) throws IOException {
        return JsonFormat.printer().print(msg);
    }

    <T extends Message.Builder> T fromJson(String json, T builder) throws IOException {
        JsonFormat.parser().merge(json, builder);
        return builder;
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

    static class MyWsListener implements WebSocketClientCallback {
        Semaphore onConnect = new Semaphore(0);
        Semaphore onDisconnect = new Semaphore(0);

        LinkedBlockingQueue<ParameterData> parameterDataList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<CommandHistoryEntry> cmdHistoryDataList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<ClientInfo> clientInfoList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<ProcessorInfo> processorInfoList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Statistics> statisticsList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<AlarmData> alarmDataList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Event> eventList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<StreamData> streamDataList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<TimeInfo> timeInfoList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<LinkEvent> linkEventList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<CommandQueueInfo> cmdQueueInfoList = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<ConnectionInfo> connInfoList = new LinkedBlockingQueue<>();

        int count = 0;

        @Override
        public void connected() {
            onConnect.release();

        }

        @Override
        public void disconnected() {
            onDisconnect.release();
        }

        @Override
        public void onMessage(WebSocketSubscriptionData data) {
            switch (data.getType()) {
            case PARAMETER:
                count++;
                parameterDataList.add(data.getParameterData());
                break;
            case CMD_HISTORY:
                cmdHistoryDataList.add(data.getCommand());
                break;
            case CLIENT_INFO:
                clientInfoList.add(data.getClientInfo());
                break;
            case PROCESSOR_INFO:
                processorInfoList.add(data.getProcessorInfo());
                break;
            case PROCESSING_STATISTICS:
                statisticsList.add(data.getStatistics());
                break;
            case ALARM_DATA:
                alarmDataList.add(data.getAlarmData());
                break;
            case EVENT:
                eventList.add(data.getEvent());
                break;
            case STREAM_DATA:
                streamDataList.add(data.getStreamData());
                break;
            case TIME_INFO:
                timeInfoList.add(data.getTimeInfo());
                break;
            case LINK_EVENT:
                linkEventList.add(data.getLinkEvent());
                break;
            case COMMAND_QUEUE_INFO:
                cmdQueueInfoList.add(data.getCommandQueueInfo());
                break;
            case CONNECTION_INFO:
                connInfoList.add(data.getConnectionInfo());
                break;
            default:
                throw new IllegalArgumentException("Unexpected type " + data.getType());
            }
        }
    }

    public static class PacketProvider extends AbstractService implements TmPacketDataLink, TmProcessor {
        static volatile PacketProvider instance;
        RefMdbPacketGenerator mdbPacketGenerator = new RefMdbPacketGenerator();
        TmSink tmSink;

        public PacketProvider(String yinstance, String name, String spec) {
            instance = this;
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
        public long getDataCount() {
            return 0;
        }

        @Override
        public void setTmSink(TmSink tmSink) {
            this.tmSink = tmSink;
        }

        @Override
        protected void doStart() {
            mdbPacketGenerator.setTmProcessor(this);
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }

        @Override
        public void processPacket(PacketWithTime pwrt) {
            tmSink.processPacket(pwrt);
        }

        @Override
        public void processPacket(PacketWithTime pwrt, SequenceContainer rootContainer) {
            tmSink.processPacket(pwrt);
        }

        @Override
        public void finished() {

        }
    }

    public static class ParameterProvider extends AbstractService implements ParameterDataLink {
        int seqNum = 0;
        ParameterSink ppListener;
        long generationTime;

        static volatile ParameterProvider instance;
        XtceDb xtcedb;

        public ParameterProvider(String yamcsInstance, String name) {
            instance = this;
            xtcedb = XtceDbFactory.getInstance(yamcsInstance);
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
        public long getDataCount() {
            return seqNum * 3;
        }

        @Override
        public void setParameterSink(ParameterSink ppListener) {
            this.ppListener = ppListener;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
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
                    .setGenerationTime(generationTime)
                    .setEngValue(ValueUtility.getDoubleGbpValue(x))
                    .build();
            ppListener.updateParams(generationTime, "IntegrationTest", seqNum, Arrays.asList(pv3));

            // mixup some ParameterValue with Protobuf ParameterValue to test compatibility with old yamcs
            org.yamcs.protobuf.Pvalue.ParameterValue pv4 = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder()
                    .setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                    .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_uint"))
                    .setGenerationTime(generationTime + 20)
                    .setEngValue(ValueUtility.getUint32GbpValue(x))
                    .build();
            ppListener.updateParams(generationTime + 20, "IntegrationTest2", seqNum, Arrays.asList(pv4));

            seqNum++;
        }

    }
}
