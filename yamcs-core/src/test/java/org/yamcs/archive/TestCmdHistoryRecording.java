package org.yamcs.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.yamcs.api.Protocol.decode;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YamcsServer;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.cmdhistory.CommandHistoryRecorder;
import org.yamcs.cmdhistory.YarchCommandHistoryAdapter;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

/**
 * Generates and saves some some command history and then it performs a replay via HornetQ
 * 
 * 
 * @author nm
 *
 */
public class TestCmdHistoryRecording extends YarchTestCase {
    static EmbeddedHornetQ hornetServer;
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        hornetServer=YamcsServer.setupHornet();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
	YamcsServer.stopHornet();
    }
 
    
    @Test
    public void testRecording() throws Exception {
        final int n=100;
        ydb.execute("create stream "+YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME+TcUplinkerAdapter.TC_TUPLE_DEFINITION.getStringDefinition());
        
        (new CommandHistoryRecorder(ydb.getName())).startAsync();
       
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
        ReplayServer replay=new ReplayServer(ydb.getName());
        replay.startAsync();
        
        YamcsSession ysession=YamcsSession.newBuilder().build();
        YamcsClient yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        
        
        CommandHistoryReplayRequest chr = CommandHistoryReplayRequest.newBuilder().build();
        ReplayRequest rr=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT).
                    setCommandHistoryRequest(chr).build();
        SimpleString replayServer=Protocol.getYarchReplayControlAddress(ydb.getName());
        StringMessage answer=(StringMessage) yclient.executeRpc(replayServer, "createReplay", rr, StringMessage.newBuilder());
        
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
        
        yclient.close();
        ysession.close();
        replay.stopAsync();
    }
}
