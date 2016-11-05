package org.yamcs;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ObjectInputStream;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.utils.ActiveMQBufferInputStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.hornetq.ArtemisManagement;
import org.yamcs.hornetq.ArtemisServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.MissionDatabaseRequest;
import org.yamcs.xtce.XtceDb;

public class YamcsServerTest {
    static EmbeddedActiveMQ artemisServer;
    
    @BeforeClass
    public static void setupYamcs() throws Exception {
        YConfiguration.setup("YamcsServer");
        ManagementService.setup(false);
        org.yamcs.yarch.management.JMXService.setup(false);
        artemisServer = ArtemisServer.setupArtemis();
        ArtemisManagement.setupYamcsServerControl();
        YamcsServer.setupYamcsServer();
    }
    
    @AfterClass
    public static void shutDownYamcs()  throws Exception {
        artemisServer.stop();
    }
    
    @Test
    public void testRetrieveMdb() throws Exception {
        YamcsSession ys=YamcsSession.newBuilder().build();
        YamcsClient yc=ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        MissionDatabaseRequest mdr = MissionDatabaseRequest.newBuilder().setDbConfigName("refmdb").build();
        yc.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getMissionDatabase", mdr, null);
        ClientMessage msg=yc.dataConsumer.receive(5000);
        assertNotNull(msg);
        ObjectInputStream ois=new ObjectInputStream(new ActiveMQBufferInputStream(msg.getBodyBuffer()));
        Object o=ois.readObject();
        assertTrue(o instanceof XtceDb);
        XtceDb xtcedb=(XtceDb) o;
        assertNotNull(xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1_1"));
        ois.close();
        
        yc.close();
        ys.close();
    }
    
}
