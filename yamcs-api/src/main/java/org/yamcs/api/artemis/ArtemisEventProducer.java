package org.yamcs.api.artemis;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.api.AbstractEventProducer;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;


/**
 * An EventProducer that publishes events over HornetQ
 * <p>
 * By default, repeated message are detected and reduced, resulting in pseudo
 * events with a message like 'last event repeated X times'. This behaviour can
 * be turned off.
 */
public class ArtemisEventProducer extends AbstractEventProducer implements ConnectionListener {
    static final String CONF_REPEATED_EVENT_REDUCTION = "repeatedEventReduction";
    
    YamcsConnector yconnector;
    SimpleString address;
    YamcsClient yclient;
    static Logger logger=LoggerFactory.getLogger(ArtemisEventProducer.class);
    
    static final int MAX_QUEUE_SIZE=1000;
    ArrayBlockingQueue<Event> queue=new ArrayBlockingQueue<Event>(MAX_QUEUE_SIZE);
    
    public ArtemisEventProducer(YamcsConnectionProperties ycd) {
        yconnector = new YamcsConnector();
        yconnector.addConnectionListener(this);
        yconnector.connect(ycd);
        address = Protocol.getEventRealtimeAddress(ycd.getInstance());
        
        InputStream is=ArtemisEventProducer.class.getResourceAsStream("/event-producer.yaml");
        boolean repeatedEventReduction = true;
        if(is!=null) {
            Object o = new Yaml().load(is);
            if(!(o instanceof Map<?,?>)) throw new RuntimeException("event-producer.yaml does not contain a map but a "+o.getClass());
    
            @SuppressWarnings("unchecked")
            Map<String,Object> m = (Map<String, Object>) o;
    
            if (m.containsKey(CONF_REPEATED_EVENT_REDUCTION)) {
                repeatedEventReduction = (Boolean) m.get(CONF_REPEATED_EVENT_REDUCTION);
            }
        }
        if (repeatedEventReduction) setRepeatedEventReduction(true);
    }
    
    
    @Override
    public void connecting(String url) { }

    @Override
    public void connected(String url) {
        try {
            yclient=yconnector.getSession().newClientBuilder().setDataProducer(true).build();
            while(!queue.isEmpty()) {
                yclient.sendData(address, ProtoDataType.EVENT, queue.poll());
            }
        } catch (ActiveMQException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
    }

    @Override
    public void disconnected() {
    }

    @Override
    public void log(String message) {
    }

    @Override
    public void close() {
        try {
            yconnector.close();
        } catch (ActiveMQException e) {
            e.printStackTrace();
        }
    }
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendEvent(org.yamcs.protobuf.Yamcs.Event)
     */
    @Override
    public synchronized void sendEvent(Event event) {
        logger.debug("Sending Event: {}", event.getMessage());
        if(yconnector.isConnected()) {
            try {
                yclient.sendData(address, ProtoDataType.EVENT, event);
            } catch (ActiveMQException e) {
                logger.error("Failed to send event ",e);
            }
        } else {
            queue.offer(event);
        }
    }
    
    @Override
    public String toString() {
        return ArtemisEventProducer.class.getName()+" connected to "+yconnector.getUrl();
    }
    @Override
    public long getMissionTime() {       
        return TimeEncoding.currentInstant();
    }
 
}
