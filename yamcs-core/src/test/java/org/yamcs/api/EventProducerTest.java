package org.yamcs.api;

import static org.junit.Assert.assertEquals;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.yarch.YarchTestCase;

public class EventProducerTest extends YarchTestCase {
    static EmbeddedActiveMQ hornetServer;
    
    @BeforeClass
    public static void setUpBeforeClass1() throws Exception {
        hornetServer=YamcsServer.setupArtemis();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
	YamcsServer.stopArtemis();
    }
    
    @Test
    public void testEventProducer() throws Exception {
        String url = "yamcs:///"+ydb.getName();
        YamcsSession ys=YamcsSession.newBuilder().setConnectionParams(url).build();
        WebSocketEventProducer ep= new WebSocketEventProducer(YamcsConnectData.parse(url));
        ep.setSource("testing");
        
        YamcsClient yc=ys.newClientBuilder().setDataConsumer(Protocol.getEventRealtimeAddress(ydb.getName()),null).build();
        
        ep.sendError("type1", "msgError");
        ep.sendWarning("type1", "msgWarning");
        ep.sendInfo("type2", "msgInfo");
        
        
        
        Event ev=(Event)yc.receiveData(Event.newBuilder());
        
        assertEquals("type1", ev.getType());
        assertEquals("msgError", ev.getMessage());
        assertEquals(0, ev.getSeqNumber());
        assertEquals("testing",ev.getSource());
        
        ev=(Event)yc.receiveData(Event.newBuilder());
        assertEquals("type1", ev.getType());
        assertEquals("msgWarning", ev.getMessage());
        assertEquals(1, ev.getSeqNumber());
        
        ev=(Event)yc.receiveData(Event.newBuilder());
        assertEquals("type2", ev.getType());
        assertEquals("msgInfo", ev.getMessage());
        assertEquals(2, ev.getSeqNumber());
        
        ((WebSocketEventProducer) ep).close();
        
        ys.close();
    }
}
