package org.yamcs.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.yamcs.api.artemis.Protocol.decode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.tctm.TcUplinkerAdapter;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.cmdhistory.YarchCommandHistoryAdapter;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.hornetq.ArtemisManagement;
import org.yamcs.hornetq.ArtemisServer;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

/**
 * Generates and saves some some command history and then it performs a replay via ActiveMQ
 * 
 * 
 * @author nm
 *
 */
public class CmdHistoryRecordingTest extends YarchTestCase {
    static EmbeddedActiveMQ artemisServer;
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        artemisServer = ArtemisServer.setupArtemis();
        ArtemisManagement.setupYamcsServerControl();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        artemisServer.stop();
    }
 
    
    @Test
    public void testRecording() throws Exception {
        final int n=100;
        ydb.execute("create stream "+YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME+TcUplinkerAdapter.TC_TUPLE_DEFINITION.getStringDefinition());
        CommandHistoryRecorder cmdHistRecorder =new CommandHistoryRecorder(ydb.getName()); 
        cmdHistRecorder.startAsync();
       
        Stream rtstream=ydb.getStream(YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME);
        assertNotNull(rtstream);
        
        for(int i=0;i<n;i++) {
            CommandId id=CommandId.newBuilder().setOrigin("testorigin").setCommandName("test"+i)
                .setGenerationTime(i).setSequenceNumber(0).build();
            PreparedCommand pc=new PreparedCommand(id);
            pc.setSource("test1(blabla)");
            pc.setBinary(new byte[20]);
            pc.setUsername("nico");
            Tuple t=pc.toTuple();
            rtstream.emitTuple(t);
        }
        
        //read back the data from the table directly in yarch
        final AtomicInteger tableReceivedCounter=new AtomicInteger(0);
        execute("create stream stream_cmdhist_out as select * from "+CommandHistoryRecorder.TABLE_NAME);
        Stream s=ydb.getStream("stream_cmdhist_out");
        final Semaphore finished=new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
          @Override
          public void streamClosed(Stream stream) {
              finished.release();
          }
          @Override
          public void onTuple(Stream stream, Tuple tuple) {
              PreparedCommand pc = PreparedCommand.fromTuple(tuple);
              int i=tableReceivedCounter.getAndIncrement();
              assertEquals("test"+i, pc.getCmdName());
          }
        });
        
        s.start();
        finished.tryAcquire(10, TimeUnit.SECONDS);

        assertEquals(n, tableReceivedCounter.get());
       
        
        //and now try remotely using replay
        Map<String, Object> config = new HashMap<>();
        config.put(ReplayServer.CONFIG_KEY_startArtemisService, true);
        ReplayServer replay=new ReplayServer(ydb.getName(), config);
        replay.startAsync();
        
        YamcsSession ysession=YamcsSession.newBuilder().build();
        YamcsClient yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        
        
        CommandHistoryReplayRequest chr = CommandHistoryReplayRequest.newBuilder().build();
        ReplayRequest rr=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT).
                    setCommandHistoryRequest(chr).build();
        SimpleString retrievalServer=Protocol.getYarchRetrievalControlAddress(ydb.getName());
        StringMessage answer=(StringMessage) yclient.executeRpc(retrievalServer, "createReplay", rr, StringMessage.newBuilder());
        
        SimpleString replayAddress=new SimpleString(answer.getMessage());
        yclient.executeRpc(replayAddress, "start", null, null);
        for(int i=0;i<n;i++) {
            ClientMessage msg=yclient.dataConsumer.receive(5000);
            assertNotNull(msg);
            ProtoDataType dt=ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
            assertEquals(ProtoDataType.CMD_HISTORY, dt);
            CommandHistoryEntry cmd=(CommandHistoryEntry)decode(msg, CommandHistoryEntry.newBuilder());
            assertEquals(i, cmd.getCommandId().getGenerationTime());
            assertEquals("test"+i, cmd.getCommandId().getCommandName());
        }
        ClientMessage msg=yclient.dataConsumer.receive(5000);
        assertNotNull(msg);
        ProtoDataType dt=ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
        assertEquals(ProtoDataType.STATE_CHANGE, dt);
        
        
        CommandHistoryReplayRequest chr1 = CommandHistoryReplayRequest.newBuilder()
                .addNameFilter(NamedObjectId.newBuilder().setName("test0").build())
                .addNameFilter(NamedObjectId.newBuilder().setName("test10").build())
                .addNameFilter(NamedObjectId.newBuilder().setName("test20").build())
                .build();
        ReplayRequest rr1=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT).
                    setCommandHistoryRequest(chr1).build();
        StringMessage answer1 = (StringMessage) yclient.executeRpc(retrievalServer, "createReplay", rr1, StringMessage.newBuilder());
        
        SimpleString replayAddress1=new SimpleString(answer1.getMessage());
        yclient.executeRpc(replayAddress1, "start", null, null);
        for(int i=0; i<3; i++) {
            msg=yclient.dataConsumer.receive(5000);
            assertNotNull(msg);
            dt = ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
            assertEquals(ProtoDataType.CMD_HISTORY, dt);
            CommandHistoryEntry cmd=(CommandHistoryEntry)decode(msg, CommandHistoryEntry.newBuilder());
            assertEquals(10*i, cmd.getCommandId().getGenerationTime());
            assertEquals("test"+(10*i), cmd.getCommandId().getCommandName());
        }
        
        msg = yclient.dataConsumer.receive(5000);
        assertNotNull(msg);
        dt = ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
        assertEquals(ProtoDataType.STATE_CHANGE, dt);
        
        yclient.close();
        ysession.close();
        replay.stopAsync();
        
        cmdHistRecorder.stopAsync();
    }
}
