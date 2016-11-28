package org.yamcs;

import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;

/**
 * Created by msc on 28/11/16.
 */
public class CrashHandler {


    private EventProducer eventProducer;
    private boolean sendingError;


    public CrashHandler(String instanceName, String source)
    {
        eventProducer= EventProducerFactory.getEventProducer(instanceName);
        eventProducer.setSource(source);
    }

    public synchronized void sendErrorEvent(String type, String msg) {
        if (sendingError) {
            return;
        }
        try {
            sendingError = true;
            eventProducer.sendError(type, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendingError = false;
    }


}
