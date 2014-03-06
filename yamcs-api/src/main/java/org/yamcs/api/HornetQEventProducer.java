package org.yamcs.api;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.hornetq.api.core.HornetQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yaml.snakeyaml.Yaml;


/**
 * An EventProducer that publishes events over HornetQ
 * <p>
 * By default, repeated message are detected and reduced, resulting in pseudo
 * events with a message like 'last event repeated X times'. This behaviour can
 * be turned off.
 */
public class HornetQEventProducer extends AbstractEventProducer implements ConnectionListener {
    static final String CONF_REPEATED_EVENT_REDUCTION = "repeatedEventReduction";
    
    YamcsConnector yconnector;
    YamcsClient yclient;
    static Logger logger=LoggerFactory.getLogger(HornetQEventProducer.class);
    
    static final int MAX_QUEUE_SIZE=1000;
    ArrayBlockingQueue<Event> queue=new ArrayBlockingQueue<Event>(MAX_QUEUE_SIZE);
    
    HornetQEventProducer(YamcsConnectData ycd) {
        yconnector=new YamcsConnector();
        yconnector.addConnectionListener(this);
        yconnector.connect(ycd);
        address=Protocol.getEventRealtimeAddress(ycd.instance);
        
        InputStream is=HornetQEventProducer.class.getResourceAsStream("/event-producer.yaml");
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
        } catch (HornetQException e) {
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

    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendEvent(org.yamcs.protobuf.Yamcs.Event)
     */
    @Override
    public synchronized void sendEvent(Event event) throws  HornetQException {
        logger.debug("Sending Event: {}", event.getMessage());
        if(yconnector.isConnected()) {
            yclient.sendData(address, ProtoDataType.EVENT, event);
        } else {
            queue.offer(event);
        }
    }
    
    @Override
    public String toString() {
        return HornetQEventProducer.class.getName()+" connected to "+yconnector.getUrl();
    }
 
}
