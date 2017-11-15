package org.yamcs.artemis;

import static org.junit.Assert.*;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;


public class ProducerKillerTest {
    static EmbeddedActiveMQ artemisServer;
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        artemisServer = new EmbeddedActiveMQ();
        artemisServer.setConfigResourcePath("artemis-tpk.xml");
        artemisServer.start();
    }
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        artemisServer.stop();
    }
    
    @Test
    public void testProducerKiller() throws Exception{
        final YamcsSession ys = YamcsSession.newBuilder().setConnectionParams("localhost", 15445).build();
        final YamcsClient dataClient = ys.newClientBuilder().setDataConsumer(null,null).build();
        dataClient.dataConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage msg) {
                NettyConnection nc=(NettyConnection)((ClientSessionImpl)ys.session).getConnection().getTransportConnection();
                nc.close();
            }
        });

        YamcsSession ys1 = YamcsSession.newBuilder().build();
        final YamcsClient yclient1 = ys1.newClientBuilder().setDataProducer(true).build();
        
        Protocol.killProducerOnConsumerClosed(yclient1.getDataProducer(), dataClient.dataAddress);
        
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int i = 0;
                    while(true) {
                        ClientMessage msg = ys.session.createMessage(false);
                        msg.getBodyBuffer().writeBytes(new byte[1500]);
                        yclient1.sendData(dataClient.dataAddress, msg);
                        i++;
                    }
                } catch (YamcsApiException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        t.join(10*60*1000);
        assertFalse(t.isAlive());
        ys1.close();
        ys.close();
    }
}
