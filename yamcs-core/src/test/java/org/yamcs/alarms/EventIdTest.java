package org.yamcs.alarms;

import static org.junit.Assert.*;

import org.junit.Test;

public class EventIdTest {

    @Test
    public void testStringParse() {
        EventId id = new EventId("/bla/bloe/blub");
        assertEquals("/bla/bloe", id.source);
        assertEquals("blub", id.type);

        id = new EventId("/yamcs/event/bla/bloe");
        assertEquals("bla", id.source);
        assertEquals("bloe", id.type);

        id = new EventId("/yamcs/event/bla/bloe/balabum");
        assertEquals("bla/bloe", id.source);
        assertEquals("balabum", id.type);
    }

    
    @Test
    public void testStringParse2() {
        EventId id = new EventId("/yamcs/event/CustomAlgorithm//YSS/SIMULATOR/Random_event_generator");
        assertEquals("CustomAlgorithm", id.source);
        assertEquals("/YSS/SIMULATOR/Random_event_generator", id.type);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStringParseException1() {
        new EventId("huh");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStringParseException2() {
        new EventId("/huh");
    }

    @Test(expected = NullPointerException.class)
    public void testNullSource() {
        new EventId(null, "type");
    }

    @Test
    public void testEquals() {

        EventId id1 = new EventId("/bla/bloe/blub");
        EventId id2 = new EventId("/bla/bloe", "blub");
        EventId id3 = new EventId("/bla/bloe", "bambarum");
        EventId id4 = new EventId("abc", "blub");

        assertTrue(id1.equals(id1));
        assertTrue(id1.equals(id2));
        assertFalse(id1.equals(null));
        assertFalse(id1.equals("bum"));
        assertFalse(id1.equals(id3));
        assertFalse(id1.equals(id4));

        assertEquals(id1.hashCode(), id2.hashCode());
        assertNotEquals(id1.hashCode(), id3.hashCode());
    }

    @Test
    public void testToString() {
        EventId id1 = new EventId("/bla/bloe/blub");
        assertEquals("/bla/bloe/blub", id1.toString());

        EventId id2 = new EventId("bla", "bloe");
        assertEquals("/yamcs/event/bla/bloe", id2.toString());
    }
}
