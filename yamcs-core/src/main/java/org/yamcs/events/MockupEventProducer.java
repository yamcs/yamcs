package org.yamcs.events;

import java.util.Queue;

import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.utils.TimeEncoding;

/**
 * saves events into a queue (to be used by unit tests)
 * 
 * 
 * @author nm
 *
 */
public class MockupEventProducer extends AbstractEventProducer {
    Queue<Event> mockupQueue;

    public MockupEventProducer(Queue<Event> mockupQueue) {
        this.mockupQueue=mockupQueue;
    }
    

    @Override
    public void sendEvent(Event event) {
        if(mockupQueue!=null) mockupQueue.add(event);
    }


    @Override
    public void close() {
    }
    
    @Override
    public long getMissionTime() {       
        return TimeEncoding.getWallclockTime();
    }
}
