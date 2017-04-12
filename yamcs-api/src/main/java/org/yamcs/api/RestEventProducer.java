package org.yamcs.api;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;

import io.netty.handler.codec.http.HttpMethod;


/**
 * An EventProducer that publishes events over WebSocket
 * <p>
 * By default, repeated message are detected and reduced, resulting in pseudo
 * events with a message like 'last event repeated X times'. This behaviour can
 * be turned off.
 */
public class RestEventProducer extends AbstractEventProducer {
    static final String CONF_REPEATED_EVENT_REDUCTION = "repeatedEventReduction";
    
    RestClient restClient;
    static Logger logger=LoggerFactory.getLogger(RestEventProducer.class);
    
    static final int MAX_QUEUE_SIZE=1000;
    ArrayBlockingQueue<Event> queue=new ArrayBlockingQueue<Event>(MAX_QUEUE_SIZE);
    
    String eventResource;
    YamcsConnectionProperties connProp;
    
    public RestEventProducer(YamcsConnectionProperties connProp) {
        restClient = new RestClient(connProp);
        this.connProp = connProp;
        
        eventResource = "/archive/"+connProp.getInstance()+"/events";
        InputStream is = RestEventProducer.class.getResourceAsStream("/event-producer.yaml");
        boolean repeatedEventReduction = true;
        if(is!=null) {
            Object o = new Yaml().load(is);
            if(!(o instanceof Map<?,?>)) {
                throw new ConfigurationException("event-producer.yaml does not contain a map but a "+o.getClass());
            }
    
            @SuppressWarnings("unchecked")
            Map<String,Object> m = (Map<String, Object>) o;
    
            if (m.containsKey(CONF_REPEATED_EVENT_REDUCTION)) {
                repeatedEventReduction = (Boolean) m.get(CONF_REPEATED_EVENT_REDUCTION);
            }
        }
        if (repeatedEventReduction) {
            setRepeatedEventReduction(true, 60000);
        }
    }
    
    


    @Override
    public void close() {
        restClient.close();
    }
    
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendEvent(org.yamcs.protobuf.Yamcs.Event)
     */
    @Override
    public synchronized void sendEvent(Event event) {
        logger.debug("Sending Event: {}", event.getMessage());
        restClient.doRequest(eventResource, HttpMethod.POST, event.toByteArray());
    }
    
    @Override
    public String toString() {
        return RestEventProducer.class.getName()+" sendign events to "+connProp;
    }
    
    @Override
    public long getMissionTime() {       
        return TimeEncoding.getWallclockTime();
    }
    
    
    public void main(String[] args) {
        
    }

}
