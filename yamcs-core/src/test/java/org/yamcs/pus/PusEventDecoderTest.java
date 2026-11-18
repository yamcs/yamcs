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
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;

class PusEventDecoderTest {
    @TempDir
    Path directory;

    @Test
    void unsigned64DecodedIdsResolveNumericTemplates() throws Exception {
        var decoder = decoder("""
                - eventId: 0
                  template: Zero
                - eventId: 1234
                  template: Small
                - eventId: 9223372036854775808
                  template: High bit
                - eventId: 18446744073709551615
                  template: Maximum
                """);
        long[] codes = { 0, 1234, Long.MIN_VALUE, -1L };
        String[] keys = { "0", "1234", "9223372036854775808", "18446744073709551615" };
        String[] messages = { "Zero", "Small", "High bit", "Maximum" };
        for (int i = 0; i < codes.length; i++) {
            String key = eventId(ValueUtility.getUint64Value(codes[i]));
            assertEquals(keys[i], key);
            assertEquals(messages[i], decoder.formatEvent(1, key, new ParameterValueList(), 1, 0, 0).getMessage());
        }
    }

    @Test
    void signedUnsigned32AndEnumeratedIdsKeepTheirMeaning() throws Exception {
        assertEquals("-1", eventId(ValueUtility.getSint32Value(-1)));
        assertEquals("-9223372036854775808", eventId(ValueUtility.getSint64Value(Long.MIN_VALUE)));
        assertEquals("4294967295", eventId(ValueUtility.getUint32Value(-1)));
        assertEquals("EVENT", eventId(ValueUtility.getEnumeratedValue(1234, "EVENT")));
    }

    private String eventId(Value value) throws Exception {
        var method = PusEventDecoder.class.getDeclaredMethod("eventIdToString", Value.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }

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
