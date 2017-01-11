package org.yamcs.api;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;


/**
 * An EventProducer that publishes events over WebSocket
 * <p>
 * By default, repeated message are detected and reduced, resulting in pseudo
 * events with a message like 'last event repeated X times'. This behaviour can
 * be turned off.
 */
public class WebSocketEventProducer extends AbstractEventProducer implements WebSocketClientCallback {
    static final String CONF_REPEATED_EVENT_REDUCTION = "repeatedEventReduction";
    
    WebSocketClient wsClient;
    YamcsClient yclient;
    static Logger logger=LoggerFactory.getLogger(WebSocketEventProducer.class);
    
    static final int MAX_QUEUE_SIZE=1000;
    ArrayBlockingQueue<Event> queue=new ArrayBlockingQueue<Event>(MAX_QUEUE_SIZE);
    
    WebSocketEventProducer(YamcsConnectionProperties connProp) {
        wsClient = new WebSocketClient(connProp, this);
        
        InputStream is=WebSocketEventProducer.class.getResourceAsStream("/event-producer.yaml");
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
        wsClient.connect();
    }
    
    

    @Override
    public void disconnected() {
    }

    @Override
    public void close() {
        wsClient.disconnect();
    }
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendEvent(org.yamcs.protobuf.Yamcs.Event)
     */
    @Override
    public synchronized void sendEvent(Event event) {
        logger.debug("Sending Event: {}", event.getMessage());
        if(wsClient.isConnected()) {
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
        return WebSocketEventProducer.class.getName()+" connected to "+wsClient;
    }
    @Override
    public long getMissionTime() {       
        return TimeEncoding.currentInstant();
    }


    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        // TODO Auto-generated method stub
        
    }
}
