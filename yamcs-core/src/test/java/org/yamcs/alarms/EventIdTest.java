package org.yamcs.alarms;

import static org.junit.Assert.assertEquals;

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
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStringParseException1() {
        new EventId("huh");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStringParseException2() {
        new EventId("/huh");
    }
}
