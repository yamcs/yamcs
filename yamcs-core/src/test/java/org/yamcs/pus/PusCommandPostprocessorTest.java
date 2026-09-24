package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.*;
import static org.yamcs.utils.StringConverter.hexStringToArray;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.utils.ValueHelper;

public class PusCommandPostprocessorTest {

    // APID 1, TC, secondary header present, unsegmented; PUS TC secondary header (17,1), source id 0
    static final String INNER_TC = "1801C00000042D11010000";

    @Test
    public void testBinaryLengthMatchesProcessedLength() {
        for (boolean crc : new boolean[] { false, true }) {
            for (boolean scheduled : new boolean[] { false, true }) {
                var pp = newPostprocessor(crc);
                var pc = newCommand(scheduled);

                int expected = pp.getBinaryLength(pc);
                byte[] processed = pp.process(pc);

                assertEquals(expected, processed.length, "crc=" + crc + ", scheduled=" + scheduled);
            }
        }
    }

    @Test
    public void testScheduledCommandFailsWithoutApid() {
        var pp = newPostprocessor(false);
        pp.pus11Apid = -1; // not configured
        var pc = newCommand(true);

        var ex = assertThrows(IllegalStateException.class,
                () -> pp.buildScheduledTc(pc.getCommandId(), 1_000_000L, 1, 1, hexStringToArray(INNER_TC)));
        assertTrue(ex.getMessage().contains("pus11Apid"),
                "expected exception message to mention pus11Apid, got: " + ex.getMessage());
    }

    @Test
    public void testScheduledTcDefaults() {
        var pp = newPostprocessor(true);
        byte[] processed = pp.process(newCommand(true));

        assertEquals(0x2D, processed[6] & 0xFF); // PUS version 2, ack flags 0xD
        assertEquals(11, processed[7]);
        assertEquals(4, processed[8]);
        assertEquals(0, ((processed[9] & 0xFF) << 8) | (processed[10] & 0xFF));
        assertEquals(0, processed[11]); // sub-schedule id, default
        assertEquals(1, processed[12]); // N
        assertEquals(0, processed[13]); // group id, default
    }

    @Test
    public void testScheduledTcConfiguredFields() {
        var pp = newPostprocessor(true);
        pp.pus11SourceId = 0x1234;
        pp.pus11AckFlags = 0x9;
        pp.pus11SubscheduleId = 3;
        pp.pus11GroupId = 4;

        byte[] processed = pp.process(newCommand(true));
        assertEquals(0x29, processed[6] & 0xFF);
        assertEquals(0x1234, ((processed[9] & 0xFF) << 8) | (processed[10] & 0xFF));
        assertEquals(3, processed[11]);
        assertEquals(4, processed[13]);

        // per-command option overrides the configured default; web clients send numbers as doubles
        var pc = newCommand(true);
        pc.addAttribute(CommandHistoryAttribute.newBuilder()
                .setName(PusCommandPostprocessor.OPTION_SUBSCHEDULE_ID.getId())
                .setValue(ValueHelper.newValue(7.0))
                .build());
        processed = pp.process(pc);
        assertEquals(7, processed[11]);
        assertEquals(4, processed[13]);

        pc.addAttribute(CommandHistoryAttribute.newBuilder()
                .setName(PusCommandPostprocessor.OPTION_GROUP_ID.getId())
                .setValue(ValueHelper.newValue(9.0))
                .build());
        processed = pp.process(pc);
        assertEquals(7, processed[11]);
        assertEquals(9, processed[13]);
    }

    @Test
    public void testScheduledTcInvalidGroupIdFailsCommand() {
        org.yamcs.utils.TimeEncoding.setUp();
        var pp = new PusCommandPostprocessor() {
            {
                timeService = new org.yamcs.time.SimulationTimeService("test");
            }
        };
        pp.setCommandHistoryPublisher(new DummyCommandHistoryPublisher());
        pp.timeEncoder = new CucTimeEncoder(0x2e, true);
        pp.pus11Apid = 5;
        var pc = newCommand(true);
        pc.addAttribute(CommandHistoryAttribute.newBuilder()
                .setName(PusCommandPostprocessor.OPTION_GROUP_ID.getId())
                .setValue(ValueHelper.newValue(256.0))
                .build());
        assertNull(pp.process(pc));
    }

    @Test
    public void testScheduledCommandRepublishesWrapperRequestIdForVerifiers() {
        var pp = newPostprocessor(false);
        var publisher = new RecordingCommandHistoryPublisher();
        pp.setCommandHistoryPublisher(publisher);

        pp.process(newCommand(true));

        // The keys the pus.xml verifiers read (ccsds-apid/ccsds-seqcount) must end up carrying the
        // wrapper's request ID (Gap #5), matching what is separately published as pus11-apid /
        // pus11-ccsds-seqcount - the inner APID is 1 (see INNER_TC), the wrapper's is pus11Apid (5).
        assertEquals(5, publisher.intValues.get(PusCommandPostprocessor.CCSDS_APID_PARA_NAME));
        assertEquals(publisher.intValues.get("pus11-apid"),
                publisher.intValues.get(PusCommandPostprocessor.CCSDS_APID_PARA_NAME));
        assertEquals(publisher.intValues.get("pus11-ccsds-seqcount"),
                publisher.intValues.get(PusCommandPostprocessor.CCSDS_SEQCOUNT_PARA_NAME));

        // The inner command's own request ID must be preserved separately (Gap #8), and must be the
        // value that was originally published under ccsds-apid/ccsds-seqcount before being
        // overwritten by the wrapper's.
        assertEquals(1, publisher.intValues.get(PusCommandPostprocessor.PUS11_INNER_APID_PARA_NAME));
        assertEquals(publisher.firstIntValues.get(PusCommandPostprocessor.CCSDS_APID_PARA_NAME),
                publisher.intValues.get(PusCommandPostprocessor.PUS11_INNER_APID_PARA_NAME));
        assertEquals(publisher.firstIntValues.get(PusCommandPostprocessor.CCSDS_SEQCOUNT_PARA_NAME),
                publisher.intValues.get(PusCommandPostprocessor.PUS11_INNER_SEQCOUNT_PARA_NAME));
    }

    @Test
    public void testUnscheduledCommandPublishesInnerApidOnly() {
        var pp = newPostprocessor(false);
        var publisher = new RecordingCommandHistoryPublisher();
        pp.setCommandHistoryPublisher(publisher);

        pp.process(newCommand(false));

        assertEquals(1, publisher.intValues.get(PusCommandPostprocessor.CCSDS_APID_PARA_NAME));
        assertFalse(publisher.intValues.containsKey(PusCommandPostprocessor.PUS11_INNER_APID_PARA_NAME));
    }

    private static PusCommandPostprocessor newPostprocessor(boolean crc) {
        var pp = new PusCommandPostprocessor();
        pp.setCommandHistoryPublisher(new DummyCommandHistoryPublisher());
        pp.timeEncoder = new CucTimeEncoder(0x2e, true);
        pp.pus11Apid = 5; // distinct from the inner command's APID (1), see Gap #9
        if (crc) {
            pp.errorDetectionCalculator = new CrcCciitCalculator();
        }
        pp.pus11Crc = crc;
        return pp;
    }

    private static PreparedCommand newCommand(boolean scheduled) {
        var pc = new PreparedCommand(hexStringToArray(INNER_TC));
        if (scheduled) {
            pc.addAttribute(CommandHistoryAttribute.newBuilder()
                    .setName(PusCommandPostprocessor.OPTION_SCHEDULE_TIME.getId())
                    .setValue(Value.newBuilder().setType(Value.Type.TIMESTAMP).setTimestampValue(1_000_000L))
                    .build());
        }
        return pc;
    }

    private static class DummyCommandHistoryPublisher implements CommandHistoryPublisher {

        @Override
        public void publish(CommandId cmdId, String key, String value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, int value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, long value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, byte[] binary) {
        }

        @Override
        public void addCommand(PreparedCommand pc) {
        }
    }

    /** Records the last and first int value published per key, so tests can check overwrites. */
    private static class RecordingCommandHistoryPublisher extends DummyCommandHistoryPublisher {
        final Map<String, Integer> intValues = new HashMap<>();
        final Map<String, Integer> firstIntValues = new HashMap<>();

        @Override
        public void publish(CommandId cmdId, String key, int value) {
            firstIntValues.putIfAbsent(key, value);
            intValues.put(key, value);
        }
    }
}
