package org.yamcs.client;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;

import org.hornetq.api.core.HornetQException;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YamcsServer;

import org.yamcs.YamcsException;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.yarch.YarchTestCase;

public class TestEventProducer extends YarchTestCase {
    static EmbeddedHornetQ hornetServer;
    
    @BeforeClass
    public static void setUpBeforeClass1() throws Exception {
        hornetServer=YamcsServer.setupHornet();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        hornetServer.stop();
    }
    
    @Test
    public void testEventProducer() throws HornetQException, YamcsException, IOException, URISyntaxException, YamcsApiException {
        YamcsSession ys=YamcsSession.newBuilder().setConnectionParams("yamcs:///").build();
        EventProducer ep=EventProducerFactory.getEventProducer(ydb.getName());
     //   System.out.println("ep: "+ep.getClass());
        ep.setSource("testing");
        
        YamcsClient yc=ys.newClientBuilder().setDataConsumer(Protocol.getEventRealtimeAddress(ydb.getName()),null).build();
        
        ep.sendError("type1", "msgError");
        ep.sendWarning("type1", "msgWarning");
        ep.sendInfo("type2", "msgInfo");
        
        
        
        Event ev=(Event)yc.receiveData(Event.newBuilder());
        
        System.out.println("got event: "+ev);
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
    }
}
