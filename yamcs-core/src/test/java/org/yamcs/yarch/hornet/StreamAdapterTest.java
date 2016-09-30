package org.yamcs.yarch.hornet;

import static org.junit.Assert.*;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.hornetq.ArtemisManagement;
import org.yamcs.hornetq.ArtemisServer;
import org.yamcs.hornetq.StreamAdapter;
import org.yamcs.hornetq.TupleTranslator;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.yarch.YarchTestCase;

public class StreamAdapterTest extends YarchTestCase {
    int n=10;
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
    public void testStream2Hornet() throws Exception {
        //setup the stream
        execute("create stream tests2h(x int, d double)");
        Stream s=ydb.getStream("tests2h");
        //setup the translator
        SimpleString hornetAddress=new SimpleString("tests2h");
        MyTupleTranslator t=new MyTupleTranslator();
        StreamAdapter streamAdapter=new StreamAdapter(s,hornetAddress,t);
        
        
        //independent stream subscribers work ok (i.e. no duplicate)
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {        }
            int k=0;
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
              //  System.out.println("independent stream subscriber received: "+tuple);
                int x=(Integer)tuple.getColumn("x");
                assertEquals(k,x);
                k++;
            }
        });
            
       
        //independent hornet subscriber
        YamcsSession ys=YamcsSession.newBuilder().build();
        
        YamcsClient msgClient=ys.newClientBuilder().setDataConsumer(hornetAddress, null).build();
        final Semaphore semaphore=new Semaphore(0);
        
        msgClient.dataConsumer.setMessageHandler(new MessageHandler() {
            int k=0;
            @Override
            public void onMessage(ClientMessage msg) {
               int x=msg.getBodyBuffer().readInt();
               double d=msg.getBodyBuffer().readDouble();
            //   System.out.println("independent hornet subscriber received: "+x+" expecting "+k);
               assertEquals(k, x);
               assertEquals(k*10, d, 0.1);
               k++;
               if(k==n)semaphore.release();
            }
        });
        
        //long t0=System.currentTimeMillis();
        //start pushing into the stream
        TupleDefinition tdef=s.getDefinition();
        for(int i=0;i<n;i++) {
            s.emitTuple(new Tuple(tdef, new Object[]{i, (double)10*i}));
        }
        assertTrue(semaphore.tryAcquire(5000, TimeUnit.SECONDS));
    //    long t1=System.currentTimeMillis();
  //      System.out.println("pushed "+n+" tuples around, speed: "+1000L*n/(t1-t0)+" tuples/sec");
        ys.close();
        streamAdapter.quit();
        execute("close stream tests2h");
    }
    
    @Test
    public void testHornet2Stream() throws Exception {
        //setup the stream
        execute("create stream testh2s(x int, d double)");
        Stream s=ydb.getStream("testh2s");
        //setup the translator
        SimpleString hornetAddress=new SimpleString("tests2h");
        MyTupleTranslator t=new MyTupleTranslator();
        StreamAdapter streamAdapter=new StreamAdapter(s,hornetAddress,t);
        
        final Semaphore semaphore=new Semaphore(0);
        
        //independent hornet subscriber
        YamcsSession ys=YamcsSession.newBuilder().build();
        YamcsClient indMsgClient= ys.newClientBuilder().setDataConsumer(hornetAddress, null).build();
        
        indMsgClient.dataConsumer.setMessageHandler(new MessageHandler() {
            int k=0;
            @Override
            public void onMessage(ClientMessage msg) {
               int x=msg.getBodyBuffer().readInt();
               double d=msg.getBodyBuffer().readDouble();
        //       System.out.println("independent hornet subscriber received: "+x);
               assertEquals(k,x);
               assertEquals(k*10,d,0.1);
               k++;
               if(k==n)semaphore.release();
            }
        });

        //independent stream subscriber
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {        }
            int k=0;
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
    //            System.out.println("independent stream subscriber received: "+tuple);
                int x=(Integer)tuple.getColumn("x");
                assertEquals(k,x);
                k++;
                semaphore.release();
            }
        });
           
        //start pushing into hornet
        YamcsClient msg1Client=ys.newClientBuilder().setDataProducer(true).build();
        
       // long t0=System.currentTimeMillis();
        for(int i=0;i<n;i++) {
            ClientMessage msg=ys.session.createMessage(false);
            msg.getBodyBuffer().writeInt(i);
            msg.getBodyBuffer().writeDouble(i*10);
            msg1Client.sendData(hornetAddress, msg);
        }
        
        assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
     //   long t1=System.currentTimeMillis();
 //       System.out.println("pushed "+n+" messages around, speed: "+1000L*n/(t1-t0)+" messages/sec");
        ys.close();
        msg1Client.close();
        streamAdapter.quit();
        execute("close stream testh2s");
    }
   
}

class MyTupleTranslator implements TupleTranslator {
    @Override
    public ClientMessage buildMessage(ClientMessage msg, Tuple tuple) {
        int x=(Integer)tuple.getColumn("x");
        double d=(Double)tuple.getColumn("d");
        ActiveMQBuffer body=msg.getBodyBuffer();
        body.writeInt(x);
        body.writeDouble(d);
        return msg;
    }

    @Override
    public Tuple buildTuple(TupleDefinition tdef, ClientMessage msg) {
        ActiveMQBuffer body=msg.getBodyBuffer();
        return new Tuple(tdef, new Object[]{body.readInt(), body.readDouble()});
    }
    
}

