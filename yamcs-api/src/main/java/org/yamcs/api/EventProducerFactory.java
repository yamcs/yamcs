package org.yamcs.api;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.api.YamcsConnectionProperties.Protocol;
import org.yamcs.api.artemis.ArtemisEventProducer;
import org.yamcs.protobuf.Yamcs.Event;
import org.yaml.snakeyaml.Yaml;

public class EventProducerFactory {
    /**
     * set to true from the unit tests
     */
    private static boolean mockup=false;
    private static Queue<Event>mockupQueue;

    static Logger log = LoggerFactory.getLogger(EventProducerFactory.class);
    
    /**
     * Configure the factory to produce mockup objects, optionally queuing the events in a queue
     * @param queue - if true then queue all messages in the mockupQueue queue.
     */
    public static void setMockup(boolean queue) {
        mockup=true;
        if(queue) {
            mockupQueue=new LinkedList<Event>();
        }
    }
    public static Queue<Event> getMockupQueue() {
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
     * 
     * If the event-producer.yml config file is not found on the classpath, returns a ConsoleEventProducer when called outside yamcs or an StreamEventProducer when called inside.
     * @param instance - instance for which the producer is to be returned
     * 
     * @return an EventProducer
     * @throws RuntimeException
     */
    public static EventProducer getEventProducer(String instance) throws RuntimeException {
        if(mockup)  {
            log.debug("Creating a ConsoleEventProducer with mockupQueue: "+mockupQueue);
            return new MockupEventProducer(mockupQueue);

        }
        String configFile = "/event-producer.yaml";
        InputStream is = EventProducerFactory.class.getResourceAsStream(configFile);
        if(is==null) {
            EventProducer producer = getStreamEventProducer(instance);     
            if(producer!=null) {
                return producer;
            }
            
            log.debug("Could not find {} in the classpath, and not running inside Yamcs, returning a ConsoleEventProducer", configFile);
            return new ConsoleEventProducer();
        }
        Yaml yaml = new Yaml();
        Object o = yaml.load(is);
        if(!(o instanceof Map<?,?>)) {
            throw new ConfigurationException("event-producer.yaml does not contain a map but a "+o.getClass());
        }

        @SuppressWarnings("unchecked")
        Map<String,String> m=(Map<String, String>)o;

        if(!m.containsKey("yamcsURL")) {
            throw new ConfigurationException("event-producer.yaml does not contain a property yamcsURL");
        } 
        String url=m.get("yamcsURL");
        YamcsConnectionProperties ycd;
        try {
            log.debug("Parsing a URL for an YamcsEventProducer: '{}'", url);
            ycd = YamcsConnectionProperties.parse(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("cannot parse yamcsURL", e);
        }

        if(instance==null) {
            if (ycd.getInstance()==null) {
                throw new IllegalArgumentException("yamcs instance has to be part of the URL");
            }
        } else {
            ycd.setInstance(instance);
        }
        EventProducer producer;
        if(ycd.getProtocol()==Protocol.artemis) { 
            log.debug("Creating an Artemis  Yamcs event producer connected to {}", ycd.getUrl());
            producer = new  ArtemisEventProducer(ycd);
        } else {
            log.debug("Creating a REST Yamcs event producer connected to {}", ycd.getUrl());
            producer = new RestEventProducer(ycd);
        }
        
        return producer;
    }
    
    
    private static EventProducer getStreamEventProducer(String instance) {
        try {
            //try to load the stream event producer from yamcs core because probably we are running inside the yamcs server
            @SuppressWarnings("unchecked")
            Class<EventProducer> ic=(Class<EventProducer>) Class.forName("org.yamcs.StreamEventProducer");
            Constructor<EventProducer> constructor = ic.getConstructor(String.class);
            return constructor.newInstance(instance);
        } catch (Exception e) {
            return null;
        }
    }
}
