package org.yamcs;


import static org.junit.Assert.*;

import java.io.ObjectInputStream;

import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.MissionDatabaseRequest;
import org.yamcs.management.ManagementService;
import org.yamcs.xtce.XtceDb;

public class YamcsServerTest {
    static EmbeddedHornetQ hornetServer;
    
    @BeforeClass
    public static void setupYamcs() throws Exception {
        YConfiguration.setup("YamcsServer");
        ManagementService.setup(false, false);
        org.yamcs.yarch.management.ManagementService.setup(false);
        hornetServer=YamcsServer.setupHornet();
        YamcsServer.setupYamcsServer();
    }
    
    @AfterClass
    public static void shutDownYamcs()  throws Exception {
	YamcsServer.stopHornet();
    }
    
    @Test
    public void testRetrieveMdb() throws Exception {
        YamcsSession ys=YamcsSession.newBuilder().build();
        YamcsClient yc=ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        MissionDatabaseRequest mdr = MissionDatabaseRequest.newBuilder().setDbConfigName("refmdb").build();
        yc.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getMissionDatabase", mdr, null);
        ClientMessage msg=yc.dataConsumer.receive(5000);
        assertNotNull(msg);
        ObjectInputStream ois=new ObjectInputStream(new ChannelBufferInputStream(msg.getBodyBuffer().channelBuffer()));
        Object o=ois.readObject();
        assertTrue(o instanceof XtceDb);
        XtceDb xtcedb=(XtceDb) o;
        assertNotNull(xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT11"));
        
        yc.close();
        ys.close();
    }
    
}
