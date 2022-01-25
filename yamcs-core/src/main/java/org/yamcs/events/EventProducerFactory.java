package org.yamcs.events;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.protobuf.Db.Event;

public class EventProducerFactory {

    /**
     * set to true from the unit tests
     */
    private static boolean mockup = false;
    private static Queue<Event> mockupQueue;

    static Logger log = LoggerFactory.getLogger(EventProducerFactory.class);

    /**
     * Configure the factory to produce mockup objects, optionally queuing the events in a queue
     * 
     * @param queue
     *            - if true then queue all messages in the mockupQueue queue.
     */
    public static void setMockup(boolean queue) {
        mockup = true;
        if (queue) {
            mockupQueue = new ConcurrentLinkedQueue<>();
        }
    }

    public static Queue<Event> getMockupQueue() {
        return mockupQueue;
    }

    static public EventProducer getEventProducer() throws RuntimeException {
        return getEventProducer(null);
    }

    /**
     * @param instance
     *            instance for which the producer is to be returned
     * 
     * @return an EventProducer
     */
    public static EventProducer getEventProducer(String instance) {
        if (mockup) {
            log.debug("Creating a ConsoleEventProducer with mockupQueue: " + mockupQueue);
            return new MockupEventProducer(mockupQueue);

        }
        if (EventProducerFactory.class.getResource("/event-producer.yaml") != null) {
            log.warn("event-producer.yaml is ignored. To post events from outside of Yamcs just use an HTTP client");
        }

        if (instance == null) {
            return new Slf4jEventProducer();
        } else {
            return new StreamEventProducer(instance);
        }
    }

    /**
     *
     * @param yamcsInstance
     * @param source
     *            source for the events
     * @param repeatedEventTimeoutMillisec
     *            suppress events that repeat in this interval
     * @return an event producer for the given instance, source and with the repeated event reduction turned on.
     */
    public static EventProducer getEventProducer(String yamcsInstance, String source,
            long repeatedEventTimeoutMillisec) {
        EventProducer eventProducer = getEventProducer(yamcsInstance);
        eventProducer.setRepeatedEventReduction(true, repeatedEventTimeoutMillisec);
        eventProducer.setSource(source);

        return eventProducer;
    }
}
