package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.utils.StringConverter.hexStringToArray;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.ValueHelper;

public class PusCommandPostprocessorTest {

    @BeforeAll
    static void initTimeEncoding() {
        org.yamcs.utils.TimeEncoding.setUp();
    }

    private static PusCommandPostprocessor newPostprocessor(YConfiguration config) {
        return newPostprocessor(config, new DummyCommandHistoryPublisher());
    }

    private static PusCommandPostprocessor newPostprocessor(YConfiguration config, CommandHistoryPublisher chp) {
        var pp = new PusCommandPostprocessor();
        pp.setCommandHistoryPublisher(chp);
        pp.init(null, config, null);
        return pp;
    }

    private static YConfiguration schedulingConfig() {
        return YConfiguration.wrap(Map.of(
                "errorDetection", Map.of("type", "CRC-16-CCIIT"),
                "timeEncoding", Map.of("implicitPfield", false, "pfield", 0x2f),
                "pus11Apid", 5,
                "pus11", Map.of(
                        "subScheduleId", Map.of("bytes", 1, "default", 1),
                        "groupId", Map.of("bytes", 1, "default", 4))));
    }

    private static YConfiguration pus22SchedulingConfig() {
        return YConfiguration.wrap(Map.of(
                "errorDetection", Map.of("type", "CRC-16-CCIIT"),
                "pus22", Map.of(
                        "subScheduleId", Map.of("bytes", 1, "default", 1),
                        "groupId", Map.of("bytes", 1, "default", 4))));
    }

    /** minimal 16 byte CCSDS TC packet, APID 100 */
    private static byte[] innerCommand() {
        byte[] b = new byte[16];
        b[0] = 0x18; // TC, APID hi = 0
        b[1] = 0x64; // APID = 100
        b[2] = (byte) 0xc0; // seq flags = 3
        return b;
    }

    private static PreparedCommand pc(byte[] binary, CommandHistoryAttribute... attrs) {
        var p = new PreparedCommand(binary);
        for (var a : attrs) {
            p.addAttribute(a);
        }
        return p;
    }

    private static CommandHistoryAttribute attr(String name, org.yamcs.protobuf.Yamcs.Value v) {
        return CommandHistoryAttribute.newBuilder().setName(name).setValue(v).build();
    }

    @Test
    public void testOptionsRegisteredWhenConfigured() {
        newPostprocessor(schedulingConfig());
        var server = YamcsServer.getServer();
        assertTrue(server.hasCommandOption("pus11SubScheduleId"));
        assertTrue(server.hasCommandOption("pus11GroupId"));
    }

    @Test
    public void testNotScheduled() {
        var pp = newPostprocessor(schedulingConfig());
        byte[] out = pp.process(pc(innerCommand()));
        // just the inner command with a 2 byte CRC appended
        assertEquals(18, out.length);
    }

    @Test
    public void testScheduledDefaultIds() {
        var pp = newPostprocessor(schedulingConfig());
        byte[] inner = pp.process(pc(innerCommand())); // 18 bytes, with inner CRC

        var pp2 = newPostprocessor(schedulingConfig());
        byte[] wrapped = pp2.process(pc(innerCommand(),
                attr("pus11ScheduleAt", ValueHelper.newTimestampValue(1_000_000_000L))));

        int tlen = pp2.timeEncoder.getEncodedLength();
        // 6 primary + 0x2D + type + subtype + 2 src + 1 subsched + 1 N + 1 group + time + inner + 2 crc
        assertEquals(14 + tlen + inner.length + 2, wrapped.length);
        assertEquals((byte) 0x2d, wrapped[6]);
        assertEquals(11, wrapped[7]);
        assertEquals(4, wrapped[8]);
        assertEquals(0, ByteArrayUtils.decodeUnsignedShort(wrapped, 9)); // source id
        assertEquals(1, wrapped[11] & 0xff); // sub-schedule id default
        assertEquals(1, wrapped[12] & 0xff); // N
        assertEquals(4, wrapped[13] & 0xff); // group id default
        assertArrayEquals(inner, Arrays.copyOfRange(wrapped, 14 + tlen, 14 + tlen + inner.length));
    }

    @Test
    public void testScheduledExplicitIds() {
        var pp = newPostprocessor(schedulingConfig());
        byte[] inner = pp.process(pc(innerCommand()));

        byte[] wrapped = newPostprocessor(schedulingConfig()).process(pc(innerCommand(),
                attr("pus11ScheduleAt", ValueHelper.newTimestampValue(2_000_000_000L)),
                attr("pus11SubScheduleId", ValueHelper.newValue(3)),
                attr("pus11GroupId", ValueHelper.newValue(7))));

        assertEquals(3, wrapped[11] & 0xff);
        assertEquals(1, wrapped[12] & 0xff);
        assertEquals(7, wrapped[13] & 0xff);
    }

    @Test
    public void testGetBinaryLengthMatchesProcessed() {
        var pp = newPostprocessor(schedulingConfig());
        var plain = pc(innerCommand());
        assertEquals(pp.process(plain).length, pp.getBinaryLength(plain));

        var scheduled = pc(innerCommand(),
                attr("pus11ScheduleAt", ValueHelper.newTimestampValue(1_500_000_000L)),
                attr("pus11SubScheduleId", ValueHelper.newValue(2)),
                attr("pus11GroupId", ValueHelper.newValue(9)));
        assertEquals(pp.process(scheduled).length, pp.getBinaryLength(scheduled));
    }

    @Test
    public void testSubScheduleAndGroupIdNotRedundantlyPublished() {
        // The sub-schedule/group ids are already recorded, correctly typed, as the pus11SubScheduleId/
        // pus11GroupId command option attributes - the postprocessor must not republish them under a
        // similarly-named "pus11-subschedule-id"/"pus11-group-id" key (that duplication is what caused them to
        // go through the wrong CommandHistoryPublisher.publish(..., long) overload, which stores a TIMESTAMP).
        var hist = new CapturingCommandHistoryPublisher();
        var pp = newPostprocessor(schedulingConfig(), hist);

        pp.process(pc(innerCommand(),
                attr("pus11ScheduleAt", ValueHelper.newTimestampValue(1_000_000_000L)),
                attr("pus11SubScheduleId", ValueHelper.newValue(3)),
                attr("pus11GroupId", ValueHelper.newValue(7))));

        assertTrue(!hist.attrs.containsKey("pus11-subschedule-id"));
        assertTrue(!hist.attrs.containsKey("pus11-group-id"));
    }

    @Test
    public void testNoPus11BlockNoExtraFields() {
        var cfg = YConfiguration.wrap(Map.of(
                "errorDetection", Map.of("type", "CRC-16-CCIIT"),
                "timeEncoding", Map.of("implicitPfield", false, "pfield", 0x2f),
                "pus11Apid", 5));
        var pp = newPostprocessor(cfg);
        byte[] inner = pp.process(pc(innerCommand()));

        byte[] wrapped = newPostprocessor(cfg).process(pc(innerCommand(),
                attr("pus11ScheduleAt", ValueHelper.newTimestampValue(3_000_000_000L))));
        int tlen = pp.timeEncoder.getEncodedLength();
        // 6 primary + 5 secondary hdr + 1 N + time + inner + 2 crc  (no sub-schedule, no group byte)
        assertEquals(12 + tlen + inner.length + 2, wrapped.length);
        assertEquals(1, wrapped[11] & 0xff); // N right after the 2 byte source id
    }

    // ---- ST[22] position-based scheduling ----

    @Test
    public void testPus22OptionsRegisteredWhenConfigured() {
        newPostprocessor(pus22SchedulingConfig());
        var server = YamcsServer.getServer();
        assertTrue(server.hasCommandOption("pus22SubScheduleId"));
        assertTrue(server.hasCommandOption("pus22GroupId"));
        // pus22OrbitNumber/pus22OrbitAngle are the base trigger, always registered (mirrors pus11ScheduleAt)
        assertTrue(server.hasCommandOption("pus22OrbitNumber"));
        assertTrue(server.hasCommandOption("pus22OrbitAngle"));
    }

    @Test
    public void testPus22ScheduledDefaultIds() {
        var pp = newPostprocessor(pus22SchedulingConfig());
        byte[] inner = pp.process(pc(innerCommand())); // 18 bytes, with inner CRC

        var pp2 = newPostprocessor(pus22SchedulingConfig());
        byte[] wrapped = pp2.process(pc(innerCommand(),
                attr("pus22OrbitNumber", ValueHelper.newValue(42)),
                attr("pus22OrbitAngle", ValueHelper.newValue(90.0))));

        // 6 primary + 0x2D + type + subtype + 2 src + 1 subsched + 1 N + 1 group + 6 position tag + inner + 2 crc
        assertEquals(14 + 6 + inner.length + 2, wrapped.length);
        assertEquals((byte) 0x2d, wrapped[6]);
        assertEquals(22, wrapped[7]);
        assertEquals(4, wrapped[8]);
        assertEquals(0, ByteArrayUtils.decodeUnsignedShort(wrapped, 9)); // source id
        assertEquals(1, wrapped[11] & 0xff); // sub-schedule id default
        assertEquals(1, wrapped[12] & 0xff); // N
        assertEquals(4, wrapped[13] & 0xff); // group id default
        assertEquals(42, ByteArrayUtils.decodeInt(wrapped, 14)); // orbit number
        int angleTicks = ByteArrayUtils.decodeUnsignedShort(wrapped, 18);
        assertEquals(Math.round(90.0 * 65536 / 360.0), angleTicks); // 90 degrees
        assertArrayEquals(inner, Arrays.copyOfRange(wrapped, 20, 20 + inner.length));
    }

    @Test
    public void testPus22ScheduledExplicitIds() {
        var pp = newPostprocessor(pus22SchedulingConfig());

        byte[] wrapped = newPostprocessor(pus22SchedulingConfig()).process(pc(innerCommand(),
                attr("pus22OrbitNumber", ValueHelper.newValue(7)),
                attr("pus22OrbitAngle", ValueHelper.newValue(0.0)),
                attr("pus22SubScheduleId", ValueHelper.newValue(3)),
                attr("pus22GroupId", ValueHelper.newValue(9))));

        assertEquals(3, wrapped[11] & 0xff);
        assertEquals(1, wrapped[12] & 0xff);
        assertEquals(9, wrapped[13] & 0xff);
        assertEquals(7, ByteArrayUtils.decodeInt(wrapped, 14));
        assertEquals(0, ByteArrayUtils.decodeUnsignedShort(wrapped, 18)); // 0 degrees -> tick 0
    }

    @Test
    public void testPus22GetBinaryLengthMatchesProcessed() {
        var pp = newPostprocessor(pus22SchedulingConfig());
        var plain = pc(innerCommand());
        assertEquals(pp.process(plain).length, pp.getBinaryLength(plain));

        var scheduled = pc(innerCommand(),
                attr("pus22OrbitNumber", ValueHelper.newValue(5)),
                attr("pus22OrbitAngle", ValueHelper.newValue(180.0)),
                attr("pus22SubScheduleId", ValueHelper.newValue(2)),
                attr("pus22GroupId", ValueHelper.newValue(9)));
        assertEquals(pp.process(scheduled).length, pp.getBinaryLength(scheduled));
    }

    @Test
    public void testPus22SubScheduleAndGroupIdNotRedundantlyPublished() {
        var hist = new CapturingCommandHistoryPublisher();
        var pp = newPostprocessor(pus22SchedulingConfig(), hist);

        pp.process(pc(innerCommand(),
                attr("pus22OrbitNumber", ValueHelper.newValue(5)),
                attr("pus22OrbitAngle", ValueHelper.newValue(45.0)),
                attr("pus22SubScheduleId", ValueHelper.newValue(3)),
                attr("pus22GroupId", ValueHelper.newValue(7))));

        assertTrue(!hist.attrs.containsKey("pus22-subschedule-id"));
        assertTrue(!hist.attrs.containsKey("pus22-group-id"));
        assertTrue(hist.attrs.get("pus22Apid") instanceof Integer);
        assertTrue(hist.attrs.get("pus22CcsdsSeqCount") instanceof Integer);
    }

    // ---- Pixxel: wrapper fields, request ID republishing (Gap #5/#8), validation ----

    // APID 1, TC, secondary header present, unsegmented; PUS TC secondary header (17,1), source id 0
    static final String INNER_TC = "1801C00000042D11010000";

    @Test
    public void testBinaryLengthMatchesProcessedLength() {
        for (boolean crc : new boolean[] { false, true }) {
            for (boolean scheduled : new boolean[] { false, true }) {
                var pp = newBarePostprocessor(crc);
                var pc = newCommand(scheduled);

                int expected = pp.getBinaryLength(pc);
                byte[] processed = pp.process(pc);

                assertEquals(expected, processed.length, "crc=" + crc + ", scheduled=" + scheduled);
            }
        }
    }

    @Test
    public void testScheduledCommandFailsWithoutApid() {
        var pp = newBarePostprocessor(false);
        pp.pus11Apid = -1; // not configured
        var pc = newCommand(true);

        var ex = assertThrows(IllegalStateException.class,
                () -> pp.buildScheduledTc(pc.getCommandId(), 1_000_000L, 1, 1, hexStringToArray(INNER_TC)));
        assertTrue(ex.getMessage().contains("pus11Apid"),
                "expected exception message to mention pus11Apid, got: " + ex.getMessage());
    }

    @Test
    public void testScheduledTcConfiguredFields() {
        var pp = newBarePostprocessor(true);
        pp.pus11SourceId = 0x1234;
        pp.pus11AckFlags = 0x9;
        pp.pus11SubScheduleIdDefault = 3;
        pp.pus11GroupIdDefault = 4;

        byte[] processed = pp.process(newCommand(true));
        assertEquals(0x29, processed[6] & 0xFF);
        assertEquals(0x1234, ((processed[9] & 0xFF) << 8) | (processed[10] & 0xFF));
        assertEquals(3, processed[11]);
        assertEquals(1, processed[12]); // N
        assertEquals(4, processed[13]);

        // per-command option overrides the configured default; web clients send numbers as doubles
        var pc = newCommand(true);
        pc.addAttribute(attr(PusCommandPostprocessor.OPTION_SUB_SCHEDULE_ID.getId(), ValueHelper.newValue(7.0)));
        processed = pp.process(pc);
        assertEquals(7, processed[11]);
        assertEquals(4, processed[13]);

        pc.addAttribute(attr(PusCommandPostprocessor.OPTION_GROUP_ID.getId(), ValueHelper.newValue(9.0)));
        processed = pp.process(pc);
        assertEquals(7, processed[11]);
        assertEquals(9, processed[13]);
    }

    @Test
    public void testScheduledTcInvalidIdFailsCommand() {
        for (var invalid : new Value[] { ValueHelper.newValue(256.0), ValueHelper.newValue(-1.0),
                ValueHelper.newValue(1.5) }) {
            var pp = new PusCommandPostprocessor() {
                {
                    timeService = new org.yamcs.time.SimulationTimeService("test");
                }
            };
            pp.setCommandHistoryPublisher(new DummyCommandHistoryPublisher());
            pp.timeEncoder = new CucTimeEncoder(0x2e, true);
            pp.pus11Apid = 5;
            pp.pus11SubScheduleIdBytes = 1;
            pp.pus11GroupIdBytes = 1;
            var pc = newCommand(true);
            pc.addAttribute(attr(PusCommandPostprocessor.OPTION_GROUP_ID.getId(), invalid));
            assertNull(pp.process(pc), "group id " + invalid);
        }
    }

    @Test
    public void testScheduledCommandRepublishesWrapperRequestIdForVerifiers() {
        var pp = newBarePostprocessor(false);
        var publisher = new RecordingCommandHistoryPublisher();
        pp.setCommandHistoryPublisher(publisher);

        pp.process(newCommand(true));

        // The keys the pus.xml verifiers read (ccsds-apid/ccsds-seqcount) must end up carrying the
        // wrapper's request ID (Gap #5), matching what is separately published as pus11Apid /
        // pus11CcsdsSeqCount - the inner APID is 1 (see INNER_TC), the wrapper's is pus11Apid (5).
        assertEquals(5, publisher.intValues.get(PusCommandPostprocessor.CCSDS_APID_PARA_NAME));
        assertEquals(publisher.intValues.get("pus11Apid"),
                publisher.intValues.get(PusCommandPostprocessor.CCSDS_APID_PARA_NAME));
        assertEquals(publisher.intValues.get("pus11CcsdsSeqCount"),
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
        var pp = newBarePostprocessor(false);
        var publisher = new RecordingCommandHistoryPublisher();
        pp.setCommandHistoryPublisher(publisher);

        pp.process(newCommand(false));

        assertEquals(1, publisher.intValues.get(PusCommandPostprocessor.CCSDS_APID_PARA_NAME));
        assertFalse(publisher.intValues.containsKey(PusCommandPostprocessor.PUS11_INNER_APID_PARA_NAME));
    }

    @Test
    public void testConfigValidation() {
        // legacy top-level keys must be moved into the pus11 block
        assertThrows(ConfigurationException.class, () -> newPostprocessor(YConfiguration.wrap(Map.of(
                "pus11Apid", 5, "pus11GroupId", 1))));
        // CRC of the wrapper requires errorDetection
        assertThrows(ConfigurationException.class, () -> newPostprocessor(YConfiguration.wrap(Map.of(
                "pus11Apid", 5, "pus11Crc", true))));
        assertThrows(ConfigurationException.class, () -> newPostprocessor(YConfiguration.wrap(Map.of(
                "pus11Apid", 0x800))));
        assertThrows(ConfigurationException.class, () -> newPostprocessor(YConfiguration.wrap(Map.of(
                "pus11", Map.of("ackFlags", 0x10)))));

        // without errorDetection the wrapper CRC defaults to off
        var pp = newPostprocessor(YConfiguration.wrap(Map.of("pus11Apid", 5)));
        assertFalse(pp.pus11Crc);
        assertTrue(newPostprocessor(schedulingConfig()).pus11Crc);
    }

    /** postprocessor configured by setting its fields: no CRC unless requested, 1 byte sub-schedule and group id */
    private static PusCommandPostprocessor newBarePostprocessor(boolean crc) {
        var pp = new PusCommandPostprocessor();
        pp.setCommandHistoryPublisher(new DummyCommandHistoryPublisher());
        pp.timeEncoder = new CucTimeEncoder(0x2e, true);
        pp.pus11Apid = 5; // distinct from the inner command's APID (1), see Gap #9
        pp.pus11SubScheduleIdBytes = 1;
        pp.pus11GroupIdBytes = 1;
        if (crc) {
            pp.errorDetectionCalculator = new CrcCciitCalculator();
        }
        pp.pus11Crc = crc;
        return pp;
    }

    private static PreparedCommand newCommand(boolean scheduled) {
        var pc = new PreparedCommand(hexStringToArray(INNER_TC));
        if (scheduled) {
            pc.addAttribute(attr(PusCommandPostprocessor.OPTION_SCHEDULE_TIME.getId(),
                    Value.newBuilder().setType(Value.Type.TIMESTAMP).setTimestampValue(1_000_000L).build()));
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

    /** records, per key, which publish(...) overload was used and with what value */
    private static class CapturingCommandHistoryPublisher extends DummyCommandHistoryPublisher {
        final Map<String, Object> attrs = new java.util.HashMap<>();

        @Override
        public void publish(CommandId cmdId, String key, int value) {
            attrs.put(key, value);
        }

        @Override
        public void publish(CommandId cmdId, String key, long value) {
            // the long overload stores a TIMESTAMP in real command history - wrap so tests can tell it apart
            // from the int overload
            attrs.put(key, java.time.Instant.ofEpochMilli(value));
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
