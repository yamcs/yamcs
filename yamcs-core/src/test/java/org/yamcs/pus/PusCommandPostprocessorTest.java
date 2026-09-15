package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
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
                "pus11", Map.of(
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
                "timeEncoding", Map.of("implicitPfield", false, "pfield", 0x2f)));
        var pp = newPostprocessor(cfg);
        byte[] inner = pp.process(pc(innerCommand()));

        byte[] wrapped = newPostprocessor(cfg).process(pc(innerCommand(),
                attr("pus11ScheduleAt", ValueHelper.newTimestampValue(3_000_000_000L))));
        int tlen = pp.timeEncoder.getEncodedLength();
        // 6 primary + 5 secondary hdr + 1 N + time + inner + 2 crc  (no sub-schedule, no group byte)
        assertEquals(12 + tlen + inner.length + 2, wrapped.length);
        assertEquals(1, wrapped[11] & 0xff); // N right after the 2 byte source id
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
}
