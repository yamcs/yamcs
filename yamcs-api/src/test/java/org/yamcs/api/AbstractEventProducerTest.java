package org.yamcs.api;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;
import java.util.Queue;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;

public class AbstractEventProducerTest {
    
    private Queue<Event> q;
    private AbstractEventProducer producer;
    
    @Before
    public void beforeTest() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
        q=EventProducerFactory.getMockupQueue();
        producer = (AbstractEventProducer) EventProducerFactory.getEventProducer();
        producer.setRepeatedEventReduction(true, 60000);
        producer.setSource("test-source");
    }
    
    @Test
    public void testEventReduction_3msgs() {
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "a-msg");
        producer.flushEventBuffer(true);
        assertMsgsEqual("a-msg", "last event repeated 2 times");
        assertSeqNosEqual(0, 2);
    }
    
    @Test
    public void testEventReduction_2msgs() {
        /*
         * Don't send out "last event repeated for only 2 msgs. There's no reduction
         * to be had in this case.
         */
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "a-msg");
        producer.flushEventBuffer(true);
        assertMsgsEqual("a-msg", "a-msg");
        assertSeqNosEqual(0, 1);
    }
    
    @Test
    public void testEventReduction_flushOne() {
        /*
         * One message should be sent out immediately without requiring a flush
         */
        producer.sendInfo("a-type", "a-msg");
        assertMsgsEqual("a-msg");
        assertSeqNosEqual(0);
    }
    
    @Test
    public void testEventReduction_interleave() {
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "another-msg");
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "another-msg");
        producer.sendInfo("a-type", "another-msg");
        producer.sendInfo("a-type", "another-msg");
        producer.sendInfo("a-type", "a-msg");
        assertMsgsEqual("a-msg", "a-msg", "another-msg", "a-msg"
                        , "another-msg", "last event repeated 2 times", "a-msg");
        assertSeqNosEqual(0, 1, 2, 3, 4, 6, 7);
    }
    
    @Test
    public void testEventReduction_turnOff() {
        producer.setRepeatedEventReduction(false, -1);
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "a-msg");
        producer.sendInfo("a-type", "a-msg");
        producer.flushEventBuffer(true);
        assertMsgsEqual("a-msg", "a-msg", "a-msg");
        assertSeqNosEqual(0, 1, 2);
    }
    
    private void assertMsgsEqual(String... msg) {
        assertEquals(q.size(), msg.length);
        Iterator<Event> e = q.iterator();
        int i = 0;
        while (e.hasNext()) {
            assertEquals("Incorrect msg at index "+i, msg[i], e.next().getMessage());
            i++;
        }
    }
    
    private void assertSeqNosEqual(int... seqNo) {
        assertEquals(q.size(), seqNo.length);
        Iterator<Event> e = q.iterator();
        int i = 0;
        while (e.hasNext()) {
            assertEquals("Incorrect seqNo at index "+i, seqNo[i], e.next().getSeqNumber());
            i++;
        }
    }
}
