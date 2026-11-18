package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.ConfigurationException;
import org.yamcs.alarms.EventId;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Event.EventSeverity;

class PusEventDecoderTest {
    @TempDir
    Path directory;

    private PusEventDecoder decoder(String yaml) throws Exception {
        Path file = directory.resolve("events.yaml");
        Files.writeString(file, yaml);
        var decoder = new PusEventDecoder();
        decoder.eventFormatter = decoder.new EventFormatter(file.toString());
        return decoder;
    }

    @Test
    void subsystemDoesNotMergeApidIdentity() throws Exception {
        var decoder = decoder("- eventId: EVENT\n  template: Event message\n  subsystem: FSW\n");
        var first = decoder.formatEvent(1, "EVENT", new ParameterValueList(), 3, 100, 42);
        var second = decoder.formatEvent(2, "EVENT", new ParameterValueList(), 3, 100, 42);
        assertEquals("APID_1", first.getSource());
        assertEquals("APID_2", second.getSource());
        assertEquals("FSW", first.getExtraOrThrow("subsystem"));
        assertEquals("FSW", second.getExtraOrThrow("subsystem"));
        assertNotEquals(new EventId(first.getSource(), first.getType()),
                new EventId(second.getSource(), second.getType()));
        assertEquals(EventSeverity.DISTRESS, first.getSeverity());
        assertEquals(100, first.getGenerationTime());
        assertEquals(42, first.getSeqNumber());
        assertEquals("Event message", first.getMessage());
    }

    @Test
    void numericAndStringTemplateIdsLoad() throws Exception {
        var decoder = decoder("""
                - eventId: 1234
                  template: Integer event
                - eventId: 4294967295
                  template: Wide event
                - eventId: '42'
                  template: Quoted event
                - eventId: EVENT
                  template: Label event
                - eventId: EVENT-7
                  template: APID-specific event
                """);
        assertEquals("Integer event", decoder.formatEvent(1, "1234", new ParameterValueList(), 1, 0, 0).getMessage());
        assertEquals("Wide event", decoder.formatEvent(1, "4294967295", new ParameterValueList(), 1, 0, 0).getMessage());
        assertNotNull(decoder.formatEvent(1, "42", new ParameterValueList(), 1, 0, 0));
        assertEquals("APID-specific event", decoder.formatEvent(7, "EVENT", new ParameterValueList(), 1, 0, 0).getMessage());
        var fallback = decoder.formatEvent(8, "EVENT", new ParameterValueList(), 1, 0, 0);
        assertEquals("Label event", fallback.getMessage());
        assertTrue(fallback.getExtraMap().isEmpty());
        assertNull(decoder.formatEvent(1, "MISSING", new ParameterValueList(), 1, 0, 0));
    }

    @Test
    void unsupportedIdsGiveConfigurationErrors() {
        for (String id : new String[] { "true", "1.5", "null", "[]", "{}" }) {
            var error = assertThrows(ConfigurationException.class,
                    () -> decoder("- eventId: " + id + "\n  template: Event\n"));
            assertTrue(error.getMessage().contains("eventId"));
        }
    }
}
