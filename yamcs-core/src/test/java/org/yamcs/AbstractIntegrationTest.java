package org.yamcs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.LinkEvent;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.security.Privilege;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.web.HttpServer;
import org.yamcs.web.websocket.ManagementResource;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.management.JMXService;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.util.concurrent.AbstractService;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLite.Builder;

import io.protostuff.JsonIOUtil;
import io.protostuff.JsonInput;
import io.protostuff.Schema;

public abstract class AbstractIntegrationTest {
    final String yamcsInstance = "IntegrationTest";
    PacketProvider packetProvider;
    ParameterProvider parameterProvider;
    YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190, "IntegrationTest");
    MyWsListener wsListener;
    WebSocketClient wsClient;
    RestClient restClient;
    protected UsernamePasswordToken adminToken = new UsernamePasswordToken("admin", "rootpassword");
    RefMdbPacketGenerator packetGenerator;
    static {  
      // Logger.getLogger("org.yamcs").setLevel(Level.ALL);
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        setupYamcs();
    }

    @Before
    public void before() throws InterruptedException {

        if(Privilege.getInstance().isEnabled())  {
            ycp.setAuthenticationToken(adminToken);
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

        //        ClientInfo cinfo = wsListener.clientInfoList.poll(5, TimeUnit.SECONDS);
        //        System.out.println("got cinfo:"+cinfo);
        //        assertNotNull(cinfo);
    }

    protected ClientInfo getClientInfo() throws InterruptedException {
        WebSocketRequest wsr = new WebSocketRequest("management", ManagementResource.OP_getClientInfo);
        wsClient.sendRequest(wsr);
        ClientInfo cinfo = wsListener.clientInfoList.poll(5, TimeUnit.SECONDS);
        assertNotNull(cinfo);
        return cinfo;
    }



    private static void setupYamcs() throws Exception {
        File dataDir=new File("/tmp/yamcs-IntegrationTest-data");

        FileUtils.deleteRecursively(dataDir.toPath());

        YConfiguration.setup("IntegrationTest");
        ManagementService.setup(false);
        JMXService.setup(false);
        new HttpServer().startServer();
        //     artemisServer = ArtemisServer.setupArtemis();
        //   ArtemisManagement.setupYamcsServerControl();
        YamcsServer.setupYamcsServer();
    }


    IssueCommandRequest getCommand(int seq, String... args) {
        IssueCommandRequest.Builder b = IssueCommandRequest.newBuilder();
        b.setOrigin("IntegrationTest");
        b.setSequenceNumber(seq);
        for(int i = 0; i<args.length; i+=2) {
            b.addAssignment(IssueCommandRequest.Assignment.newBuilder().setName(args[i]).setValue(args[i+1]).build());
        }

        return b.build();
    }

    protected NamedObjectList getSubscription(String... pfqname) {
        NamedObjectList.Builder b = NamedObjectList.newBuilder();
        for(String p: pfqname) {
            b.addList(NamedObjectId.newBuilder().setName(p).build());
        }
        return b.build();
    }

    @After
    public void after() throws InterruptedException {
        wsClient.disconnect();
        assertTrue(wsListener.onDisconnect.tryAcquire(5, TimeUnit.SECONDS));
    }

    @AfterClass
    public static void shutDownYamcs()  throws Exception {
        YamcsServer.shutDown();
    }


    <T extends MessageLite> String toJson(T msg, Schema<T> schema) throws IOException {
        StringWriter writer = new StringWriter();
        JsonIOUtil.writeTo(writer, msg, schema, false);
        return writer.toString();
    }

    <T extends MessageLite.Builder> T fromJson(String jsonstr, Schema<T> schema) throws IOException {
        StringReader reader = new StringReader(jsonstr);
        T msg = schema.newMessage();
        JsonIOUtil.mergeFrom(reader, msg, schema, false);
        return msg;
    }

    //parses a series of messages (not really a list because they are not separated by "," and do not have start and end of list ([ ])
    <T extends MessageLite> List<T> allFromJson(String jsonstr, Schema schema) throws IOException {

        StringReader reader = new StringReader(jsonstr);
        JsonParser parser = JsonIOUtil.DEFAULT_JSON_FACTORY.createParser(reader);
        JsonInput input = new JsonInput(parser);

        List<T> r = new ArrayList<>();
        while(true) {
            JsonToken t = parser.nextToken();
            if(t==null) break;
            if (t != JsonToken.START_OBJECT) throw new RuntimeException("Expected: "+JsonToken.START_OBJECT+", got: "+t);
            MessageLite.Builder msgb = (Builder) schema.newMessage();
            schema.mergeFrom(input, msgb);
            r.add((T)msgb.build());
        }
        return r;
    }

    class MyWsListener implements WebSocketClientCallback {
        Semaphore onConnect = new Semaphore(0);
        Semaphore onDisconnect = new Semaphore(0);

        LinkedBlockingQueue<NamedObjectId> invalidIdentificationList = new LinkedBlockingQueue<>();
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

        int count =0;
        @Override
        public void connected() {
            onConnect.release();

        }

        @Override
        public void disconnected() {
            onDisconnect.release();
        }

        @Override
        public void onInvalidIdentification(NamedObjectId id) {
            invalidIdentificationList.add(id);
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
        public String getLinkStatus() {
            return "OK";
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
            mdbPacketGenerator.init(null, this);
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
        public String getLinkStatus() {
            return "OK";
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
            return seqNum*3;
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
            pv2.setStringValue("para" +x);
            

            ppListener.updateParameters(generationTime, "IntegrationTest", seqNum, Arrays.asList(pv1, pv2));
            
            //this one should be combined with the two above in the archive as they have the same generation time, group and sequence
            org.yamcs.protobuf.Pvalue.ParameterValue pv3 = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder().setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                    .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_double"))
                    .setGenerationTime(generationTime)
                    .setEngValue(ValueUtility.getDoubleGbpValue(x))
                    .build();
            ppListener.updateParams(generationTime, "IntegrationTest", seqNum, Arrays.asList(pv3));
            
            
            //mixup some ParameterValue with Protobuf ParameterValue to test compatibility with old yamcs
            org.yamcs.protobuf.Pvalue.ParameterValue pv4 = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder().setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                    .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/processed_para_uint"))
                    .setGenerationTime(generationTime+20)
                    .setEngValue(ValueUtility.getUint32GbpValue(x))
                    .build();
            ppListener.updateParams(generationTime+20, "IntegrationTest", seqNum, Arrays.asList(pv4));
            
            seqNum++;
        }
    }
}
