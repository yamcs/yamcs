package org.yamcs.api;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.YamcsConnectionProperties.Protocol;
import org.yamcs.api.artemis.ArtemisEventProducer;
import org.yamcs.protobuf.Yamcs.Event;
import org.yaml.snakeyaml.Yaml;

public class EventProducerFactory {
    /**
     * set to true from the unit tests
     */
    static private boolean mockup=false;
    static private Queue<Event>mockupQueue;

    static Logger log = LoggerFactory.getLogger(EventProducerFactory.class);
    
    /**
     * Configure the factory to produce mockup objects, optionally queuing the events in a queue
     * @param queue - if trye then queue all messages in the mockupQueue queue.
     */
    static public void setMockup(boolean queue) {
        mockup=true;
        if(queue) {
            mockupQueue=new LinkedList<Event>();
        }
    }
    static public Queue<Event> getMockupQueue() {
        return mockupQueue;
    }

    /**
     * Creates an event producer based on the event-producer.yaml properties loaded from the classpath.
     * The yamcsURL in the file has to contain the yamcs instance.
     * If the event-producer.yaml is not found, then a console event producer is created that just
     * prints the messages on console.
     * @return the created event producer
     * @throws RuntimeException in case the config files is found but contains errors
     */
    static public EventProducer getEventProducer() throws RuntimeException {
        return getEventProducer(null);
    }

    /**
     *
     * The instance passed as parameter will overwrite the instance in the config file if any.
     * For yamcs internal services: leave the url as yamcs:/// and specify the instance with this method
     * 
     * @return
     * @throws RuntimeException
     */
    static public EventProducer getEventProducer(String instance) throws RuntimeException {

        if(mockup)  {
            log.debug("Creating a ConsoleEventProducer with mockupQueue: "+mockupQueue);
            return new MockupEventProducer(mockupQueue);

        }
        String configFile = "/event-producer.yaml";
        InputStream is = EventProducerFactory.class.getResourceAsStream(configFile);
        if(is==null) {
            log.debug("Could not find {} in the classpath, returning a ConsoleEventProducer", configFile);
            return new ConsoleEventProducer();
        }
        Yaml yaml=new Yaml();
        Object o=yaml.load(is);
        if(!(o instanceof Map<?,?>)) {
            throw new RuntimeException("event-producer.yaml does not contain a map but a "+o.getClass());
        }

        @SuppressWarnings("unchecked")
        Map<String,String> m=(Map<String, String>)o;

        if(!m.containsKey("yamcsURL")) {
            throw new RuntimeException("event-producer.yaml does not contain a property yamcsURL");
        } 
        String url=m.get("yamcsURL");
        YamcsConnectionProperties ycd;
        try {
            log.debug("Parsing a URL for an YamcsEventProducer: '{}'", url);
            ycd = YamcsConnectionProperties.parse(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException("cannot parse yamcsURL", e);
        }

        if(instance==null) {
            if (ycd.getInstance()==null) throw new RuntimeException("yamcs instance has to be part of the URL");
        } else {
            ycd.setInstance(instance);
        }
        EventProducer producer = null;
        if(ycd.getHost()==null) {
            try {
                //try to load the stream event producer from yamcs core because probably we are running inside the yamcs server
                @SuppressWarnings("unchecked")
                Class<EventProducer> ic=(Class<EventProducer>) Class.forName("org.yamcs.StreamEventProducer");
                Constructor<EventProducer> constructor = ic.getConstructor(String.class);
                producer =  constructor.newInstance(instance);
            } catch (Exception e) {
                log.warn("Failed to load the internal StreamEventProducer", e);
            }
        }

        if(producer==null) {
            if(ycd.getProtocol()==Protocol.ARTEMIS) { 
                log.debug("Creating an Artemis  Yamcs event producer connected to {}", ycd.getUrl());
                producer = new  ArtemisEventProducer(ycd);
            } else {
                log.debug("Creating a WebSocket Yamcs event producer connected to {}", ycd.getUrl());
                producer = new WebSocketEventProducer(ycd);
            }
        }
        
        return producer;
    }
}
