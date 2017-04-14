package org.yamcs.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.yamcs.api.artemis.Protocol.decode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.StreamEventProducer;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.artemis.ArtemisEventProducer;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.api.artemis.YamcsClient.ClientBuilder;
import org.yamcs.artemis.ArtemisServer;
import org.yamcs.artemis.EventTupleTranslator;
import org.yamcs.artemis.StreamAdapter;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

/**
 * Generates and saves some some events and then it performs a replay via ActiveMQ
 * 
 * 
 * @author nm
 *
 */
public class EventRecordingTest extends YarchTestCase {
    static EmbeddedActiveMQ artemisServer;
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        artemisServer = ArtemisServer.setupArtemis();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        artemisServer.stop();
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
        ydb.execute("create stream event_dump(gentime timestamp, source enum, seqNum int, body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'))");
        EventRecorder eventRecorder = new EventRecorder(instance);
        final int n=10;
        eventRecorder.startAsync();
        eventRecorder.awaitRunning();
        
        YamcsSession ys=YamcsSession.newBuilder().build();
        ClientBuilder pcb = ys.newClientBuilder();
        SimpleString address = new SimpleString(instance+".events_realtime");
        Stream rtstream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        assertNotNull(rtstream);
        
        StreamAdapter streamAdapter = new StreamAdapter(rtstream, address, new EventTupleTranslator());
        ArtemisEventProducer aep = new ArtemisEventProducer(YamcsConnectionProperties.parse("yamcs:///"+instance));
        Thread.sleep(1000);
        pcb.setDataProducer(true).setDataConsumer(address,null);
        YamcsClient msgClient = pcb.build();
        final AtomicInteger artemisReceivedCounter=new AtomicInteger(0);
        msgClient.dataConsumer.setMessageHandler (
            new MessageHandler() {
                @Override
                public void onMessage(ClientMessage msg) {
                    try {
                        Event ev=(Event)decode(msg, Event.newBuilder());
                        assertEquals(artemisReceivedCounter.getAndIncrement(), ev.getSeqNumber());
                    } catch (YamcsApiException e) {
                       fail("Exception received"+e);
                    }
                }
                
            }
        );
      
        for(int i=0;i<n;i++) {
            Event event=Event.newBuilder().setGenerationTime(i*1000).setMessage(i+" message "+i).
            setSeqNumber(i).setReceptionTime(18900).setSource("TEST"+(i&0xF)).build();
            aep.sendEvent(event);
        }
        Thread.sleep(1000);//wait for the event recorder to receive and record artemis events
        
        //send now a bunch of events directly on the stream
        StreamEventProducer sep = new StreamEventProducer(instance);
        for(int i=n;i<2*n;i++) {
            Event event=Event.newBuilder().setGenerationTime(i*1000).setMessage(i+" message "+i).
            setSeqNumber(i).setReceptionTime(18900).setSource("TEST"+(i&0xF)).build();
            sep.sendEvent(event);
        }
        
        //read back all the data from the table
        List<Tuple> eventList = fetchAllFromTable(EventRecorder.TABLE_NAME);
        
        assertEquals(2*n, eventList.size());
        int i =0;
        for(Tuple tuple: eventList) {
            Event ev=(Event)tuple.getColumn("body");
            checkEvent(i, ev);
            i++;
        }
        assertEquals(2*n, artemisReceivedCounter.get());
        aep.close();
        streamAdapter.quit();
        msgClient.close();
    }
}
