package org.yamcs.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.yamcs.api.Protocol.decode;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YamcsServer;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsClient.ClientBuilder;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.EventReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.hornetq.EventTupleTranslator;
import org.yamcs.hornetq.StreamAdapter;

/**
 * Generates and saves some some events and then it performs a replay via HornetQ
 * 
 * 
 * @author nm
 *
 */
public class TestEventRecording extends YarchTestCase {
    static EmbeddedHornetQ hornetServer;
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        hornetServer=YamcsServer.setupHornet();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        hornetServer.stop();
    }
    private void checkEvent(int i, Event ev) {
        assertEquals(i, ev.getSeqNumber());
        assertEquals(i*1000, ev.getGenerationTime());
        assertEquals("TEST"+(i&0xF),ev.getSource());
        assertEquals(EventSeverity.INFO, ev.getSeverity());
        assertEquals(18900,ev.getReceptionTime());
    }
    
    @Test
    public void testRecording() throws Exception {
	ydb.execute("create stream "+EventRecorder.REALTIME_EVENT_STREAM_NAME+"(gentime timestamp, source enum, seqNum int, body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'))");
	ydb.execute("create stream "+EventRecorder.DUMP_EVENT_STREAM_NAME+"(gentime timestamp, source enum, seqNum int, body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'))");
	
        final int n=100;
        (new EventRecorder(context.getDbName())).startAsync();
        YamcsSession ys=YamcsSession.newBuilder().build();
        ClientBuilder pcb=ys.newClientBuilder();
        SimpleString address=new SimpleString("events_realtime");
        Stream rtstream=ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        assertNotNull(rtstream);
        
        StreamAdapter streamAdapter= new StreamAdapter(rtstream, address, new EventTupleTranslator());
        pcb.setDataProducer(true).setDataConsumer(address,null);
        YamcsClient msgClient=pcb.build();
        final AtomicInteger hornetReceivedCounter=new AtomicInteger(0);
        msgClient.dataConsumer.setMessageHandler (
            new MessageHandler() {
                @Override
                public void onMessage(ClientMessage msg) {
                    try {
                        Event ev=(Event)decode(msg, Event.newBuilder());
                        assertEquals(ev.getSeqNumber(),hornetReceivedCounter.getAndIncrement());
                     //   System.out.println("got event "+ev);
                    } catch (YamcsApiException e) {
                       fail("Exception received"+e);
                    }
                    
                }
                
            }
        );
      
        for(int i=0;i<n;i++) {
            Event event=Event.newBuilder().setGenerationTime(i*1000).setMessage(i+" message "+i).
            setSeqNumber(i).setReceptionTime(18900).setSource("TEST"+(i&0xF)).build();
            msgClient.sendData(address, ProtoDataType.EVENT, event);
        }
        Thread.sleep(3000);//wait for the event recorder to record the events
        
        //now try to read back the data from the table directly in yarch
        final AtomicInteger tableReceivedCounter=new AtomicInteger(0);
        execute("create stream stream_events_out as select * from events");
        Stream s=ydb.getStream("stream_events_out");
        final Semaphore finished=new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
          @Override
          public void streamClosed(Stream stream) {
            
          }
          @Override
          public void onTuple(Stream stream, Tuple tuple) {
              Event ev=(Event)tuple.getColumn("body");
              checkEvent(tableReceivedCounter.get(),ev);
              tableReceivedCounter.incrementAndGet();
              if(tableReceivedCounter.get()==n)finished.release();
          }
        });
        s.start();
        finished.tryAcquire(10, TimeUnit.SECONDS);

        assertEquals(n, tableReceivedCounter.get());
        assertEquals(n, hornetReceivedCounter.get());
        msgClient.close();
        
        
        //and now try remotely using replay
        ReplayServer replay=new ReplayServer(ydb.getName());
        replay.startAsync();
        msgClient=ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        
        
        EventReplayRequest err=EventReplayRequest.newBuilder().build();
        ReplayRequest rr=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT).
                    setEventRequest(err).build();
        SimpleString replayServer=Protocol.getYarchReplayControlAddress(context.getDbName());
        StringMessage answer=(StringMessage) msgClient.executeRpc(replayServer, "createReplay", rr, StringMessage.newBuilder());
        
        SimpleString replayAddress=new SimpleString(answer.getMessage());
        msgClient.executeRpc(replayAddress, "start", null, null);
        for(int i=0;i<n;i++) {
            ClientMessage msg=msgClient.dataConsumer.receive(5000);
            assertNotNull(msg);
            ProtoDataType dt=ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
            assertEquals(ProtoDataType.EVENT, dt);
            Event ev=(Event)decode(msg, Event.newBuilder());
            checkEvent(i,ev);
        }
        ClientMessage msg=msgClient.dataConsumer.receive(5000);
        assertNotNull(msg);
        ProtoDataType dt=ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
        assertEquals(ProtoDataType.STATE_CHANGE, dt);
        
        streamAdapter.quit();
        msgClient.close();
        replay.stopAsync();
    }
}
