package org.yamcs.yarch.hornet;

import static org.junit.Assert.*;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.core.client.impl.DelegatingSession;
import org.hornetq.core.remoting.impl.netty.NettyConnection;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.HornetQAuthManager;

import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;


public class TestProducerKiller {
    static EmbeddedHornetQ hornetServer;
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        hornetServer = new EmbeddedHornetQ();
        hornetServer.setConfigResourcePath("hornetq-configuration-tpk.xml");
        hornetServer.setSecurityManager( new HornetQAuthManager() );
        hornetServer.start();
    }
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        hornetServer.stop();
    }
    
    @Test
    public void testProducerKiller() throws Exception{
        final YamcsSession ys=YamcsSession.newBuilder().setConnectionParams("localhost", 15445).build();
        final YamcsClient dataClient=ys.newClientBuilder().setDataConsumer(null,null).build();
        dataClient.dataConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage msg) {
            //    System.out.println(Thread.currentThread()+" received the first message: "+msg+", closing connection");
                NettyConnection nc=(NettyConnection)((DelegatingSession)ys.session).getConnection().getTransportConnection();
                nc.close();
            }
        });

        YamcsSession ys1=YamcsSession.newBuilder().build();
        final YamcsClient yclient1=ys1.newClientBuilder().setDataProducer(true).build();
        
        Protocol.killProducerOnConsumerClosed(yclient1.dataProducer, dataClient.dataAddress);
        
        Thread t=new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int i=0;
                    while(true) {
                      //  System.out.println("sending message "+i);
                        ClientMessage msg=ys.session.createMessage(false);
                        msg.getBodyBuffer().writeBytes(new byte[1500]);
                        yclient1.dataProducer.send(dataClient.dataAddress, msg);
                        i++;
                    }
                } catch (HornetQException e) {
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
