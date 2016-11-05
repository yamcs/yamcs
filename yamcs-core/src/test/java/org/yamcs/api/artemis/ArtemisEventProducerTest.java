package org.yamcs.api.artemis;

import static org.junit.Assert.assertEquals;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.hornetq.ArtemisManagement;
import org.yamcs.hornetq.ArtemisServer;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.yarch.YarchTestCase;

public class ArtemisEventProducerTest extends YarchTestCase {
    static EmbeddedActiveMQ artemisServer;
    
    @BeforeClass
    public static void setUpBeforeClass1() throws Exception {
        artemisServer = ArtemisServer.setupArtemis();
        ArtemisManagement.setupYamcsServerControl();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        artemisServer.stop();
    }
    
    @Test
    public void testEventProducer() throws Exception {
        String url = "yamcs:///"+ydb.getName();
        YamcsConnectionProperties connProp = YamcsConnectionProperties.parse(url);
        ArtemisEventProducer ep= new ArtemisEventProducer(connProp);
        ep.setSource("testing");
        YamcsSession ys = YamcsSession.newBuilder().setConnectionParams(connProp).build();
        
        YamcsClient yc = ys.newClientBuilder().setDataConsumer(Protocol.getEventRealtimeAddress(ydb.getName()),null).build();
        
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
        
        ep.close();
        
        ys.close();
    }
}
