package org.yamcs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.protobuf.Db.Event;

public class EventCrashHandlerTest {

    @Test
    public void sendErrorEventOk() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
        Queue<Event> eventQueue = EventProducerFactory.getMockupQueue();
        EventCrashHandler crashHandler = new EventCrashHandler("unitTestInstance");
        crashHandler.handleCrash("m1", "err1");
        crashHandler.handleCrash("m1", "err2");

        assertEquals(2, eventQueue.size());
    }
}
