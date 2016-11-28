package org.yamcs;

import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

import java.util.Queue;

/**
 * Created by msc on 28/11/16.
 */
public class CrashHandlerTest {
    @Test
    public void sendErrorEventOk() {

        // Arrange
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
        Queue<Yamcs.Event> eventQueue = EventProducerFactory.getMockupQueue();
        CrashHandler target = new CrashHandler("unitTestInstance", "test1");

        // Act
        target.sendErrorEvent("m1", "err1");
        target.sendErrorEvent("m1", "err2");

        // Assert
        assert (eventQueue.size() == 2);

    }
}
