package org.yamcs;

import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;

/**
 * Crash handler that reports events via an event producer
 * 
 * Created by msc on 28/11/16.
 */
public class EventCrashHandler implements CrashHandler {
    private EventProducer eventProducer;
    private boolean sendingError;


    public EventCrashHandler(String instanceName) {
        eventProducer= EventProducerFactory.getEventProducer(instanceName);
        eventProducer.setSource("CrashHandler");
    }

    public synchronized void handleCrash(String type, String msg) {
        if (sendingError) {
            return;
        }
        try {
            sendingError = true;
            eventProducer.sendSevere(type, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendingError = false;
    }
}
