package org.yamcs;

import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.utils.TimeEncoding;

import java.util.Queue;

/**
 * Created by msc on 28/11/16.
 */
public class EventCrashHandlerTest {
    @Test
    public void sendErrorEventOk() {

        // Arrange
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
        Queue<Yamcs.Event> eventQueue = EventProducerFactory.getMockupQueue();
        EventCrashHandler target = new EventCrashHandler("unitTestInstance");

        // Act
        target.handleCrash("m1", "err1");
        target.handleCrash("m1", "err2");

        // Assert
        assert (eventQueue.size() == 2);

    }
}
