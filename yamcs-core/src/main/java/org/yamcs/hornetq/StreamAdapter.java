package org.yamcs.hornetq;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;

/**
 * converts between hornet addresses and yamcs streams
 * 
 * To avoid a ping-pong effect:
 *  - it creates a queue with a filter on hornet side
 *  - it remembers a thread local version of the tuple in transition on yarch side
 *
 *
 * this class is deprecated because it converts data in both directions whereas we want to be able to do it separately and have the incoming data as Data Links.
 * 
 *  The ActiveMQ XYZ Providers shall be used for ActiveMQ to stream translator and the ActiveMQXYZService (based on AbstractActiveMQTranslatorService) in the other direction.
 *  
 *  Currently used only for events - once event providers are also shown as data links, we should get rid of this.
 *  
 */
@Deprecated
public class StreamAdapter implements StreamSubscriber, MessageHandler {
    final private SimpleString hornetAddress;
    final private TupleTranslator translator;
    YamcsSession yamcsSession;
    final private YamcsClient yClient;
    final private Stream stream;
    static final public String UNIQUEID_HDR_NAME="_y_uniqueid";
    static final public int UNIQUEID = new Random().nextInt();
    
    
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    ThreadLocal<Tuple> currentProcessingTuple=new ThreadLocal<Tuple>();
    
    public StreamAdapter(Stream stream, SimpleString hornetAddress, TupleTranslator translator) throws ActiveMQException, YamcsApiException {
        this.stream=stream;
        this.hornetAddress=hornetAddress;
        this.translator=translator;
        SimpleString queue=new SimpleString(hornetAddress.toString()+"-StreamAdapter");
        
        yamcsSession=YamcsSession.newBuilder().build();
        yClient=yamcsSession.newClientBuilder().setDataProducer(true).setDataConsumer(hornetAddress, queue).
            setFilter(new SimpleString(UNIQUEID_HDR_NAME+"<>"+UNIQUEID)).
            build();
        
        yClient.dataConsumer.setMessageHandler(this);
        stream.addSubscriber(this);
    }

    
    @Override
    public void onTuple(Stream s, Tuple tuple) {
        if(tuple==currentProcessingTuple.get())return;
        try {
            ClientMessage msg=translator.buildMessage(yamcsSession.session.createMessage(false), tuple);
            msg.putIntProperty(UNIQUEID_HDR_NAME, UNIQUEID);
            yClient.sendData(hornetAddress, msg);
        } catch (IllegalArgumentException e) {
            log.warn(e.getMessage());
        } catch (ActiveMQException e) {
            log.warn("Got exception when sending message:", e);
        }
    }

    @Override
    public void streamClosed(Stream istream) {
    }

    @Override
    public void onMessage(ClientMessage msg) {
        //System.out.println("mark 1: message received: "+msg);
        try {
            Tuple tuple=translator.buildTuple(stream.getDefinition(), msg);
            currentProcessingTuple.set(tuple);
            stream.emitTuple(tuple);
        } catch(IllegalArgumentException e){
            log.warn( "{} for message: {}", e.getMessage(), msg);
        }
    }


    public void quit() {
        try {
            yamcsSession.close();
        } catch (ActiveMQException e) {
            log.warn("Got exception when quiting:", e);
        }
        stream.removeSubscriber(this);
        
    }

    @Override
    public String toString() {
        return "StreamAdapter["+stream.getName()+"<->"+hornetAddress+"]";
    }
}
