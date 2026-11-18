package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.Event;

class EventFilterTest {
    @Test
    void filtersExtraFieldsUsingStringComparisons() throws Exception {
        var event = Event.newBuilder().setSource("APID_1").setType("READY")
                .setGenerationTime(0).setSeqNumber(1).setMessage("Ready")
                .putExtra("subsystem", "FSW").build();
        var tuple = new Tuple(StandardTupleDefinitions.EVENT,
                new Object[] { 0L, "APID_1", 1, event });
        assertTrue(new EventFilter("extra.subsystem = FSW").matches(tuple));
        assertFalse(new EventFilter("extra.subsystem = PAYLOAD").matches(tuple));
        assertTrue(new EventFilter("source = APID_1 AND extra.subsystem : SW").matches(tuple));
        assertTrue(new EventFilter("NOT extra.subsystem = PAYLOAD").matches(tuple));
        assertFalse(new EventFilter("extra.missing = FSW").matches(tuple));
    }
}
