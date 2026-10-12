package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.MetaCommandProcessor;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.AncillaryData;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.IntegerArgumentType;

/**
 * Tests the processing by the {@link PusCommandPostprocessor} of the TC packets embedded in arguments annotated with
 * {@link AncillaryData#KEY_EMBEDDED_TC}
 */
public class PusEmbeddedTcTest {
    // 16 bytes TC packet, APID 100, zero sequence count and length
    static final String INNER1 = "1864C000000011223344556677889900";
    // 10 bytes TC packet, APID 100
    static final String INNER2 = "1864C0000000AABBCCDD";

    Mdb mdb;
    MetaCommandProcessor metaCommandProcessor;

    @BeforeAll
    static void beforeAll() {
        TimeEncoding.setUp();
    }

    @BeforeEach
    public void setup() {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("EmbeddedTcCommandTest");
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    private PreparedCommand buildCommand(String name, Map<String, Object> args) throws ErrorInCommand {
        var mc = mdb.getMetaCommand("/EmbeddedTcTest/" + name);
        var cbr = metaCommandProcessor.buildCommand(mc, args, 0);
        CommandId cmdId = CommandId.newBuilder().setCommandName(mc.getQualifiedName()).setOrigin("test")
                .setSequenceNumber(1).setGenerationTime(0).build();
        var pc = new PreparedCommand(cmdId);
        pc.setMetaCommand(mc);
        pc.setBinary(cbr.getCmdPacket());
        pc.setArgAssignment(cbr.getArgs(), args.keySet());
        pc.setArgumentLocations(cbr.getArgumentLocations());
        // pass it through the tuple like when sent via the tc stream
        return PreparedCommand.fromTuple(pc.toTuple(), mdb);
    }

    private static PusCommandPostprocessor newPostprocessor(boolean crc, CommandHistoryPublisher chp) {
        var pp = new PusCommandPostprocessor();
        pp.setCommandHistoryPublisher(chp);
        pp.init(null, YConfiguration.wrap(Map.of(
                "errorDetection", Map.of("type", "CRC-16-CCIIT"),
                "embeddedTcCrc", crc)), null);
        return pp;
    }

    private static void assertValidPacket(byte[] b, int offset, int length, boolean withCrc) {
        assertEquals(length - 7, ByteArrayUtils.decodeUnsignedShort(b, offset + 4), "packet length");
        if (withCrc) {
            int crc = new CrcCciitCalculator().compute(b, offset, length - 2);
            assertEquals(crc, ByteArrayUtils.decodeUnsignedShort(b, offset + length - 2), "CRC");
        }
    }

    private static int seqCount(byte[] b, int offset) {
        return ByteArrayUtils.decodeUnsignedShort(b, offset + 2) & 0x3FFF;
    }

    @Test
    public void testArgumentType() throws ErrorInCommand {
        var pc = buildCommand("list", listArgs());
        assertTrue(pc.getArgumentType("activities[1].tc") instanceof BinaryArgumentType);
        assertTrue(pc.getArgumentType("activities[0].group_id") instanceof IntegerArgumentType);
        assertTrue(((BinaryArgumentType) pc.getArgumentType("activities[1].tc"))
                .hasAncillaryData(AncillaryData.KEY_EMBEDDED_TC));
        assertNull(pc.getArgumentType("activities[0].nosuchmember"));
        assertNull(pc.getArgumentType("n[0]"));
        assertNull(pc.getArgumentType("nosucharg"));
    }

    @Test
    public void testSingleWithCrc() throws ErrorInCommand {
        var hist = new CapturingCommandHistoryPublisher();
        var pp = newPostprocessor(true, hist);
        var pc = buildCommand("single", Map.of("x", 5, "tc", INNER1, "y", 0x1234));
        assertEquals(11 + 1 + 16 + 2, pc.getBinary().length);

        byte[] out = pp.process(pc);
        // embedded TC grows by 2 (CRC), plus the outer CRC
        assertEquals(11 + 1 + 18 + 2 + 2, out.length);
        assertEquals(out.length, pp.getBinaryLength(pc));

        assertEquals(5, out[11]);
        assertValidPacket(out, 12, 18, true);
        assertArrayEquals(Arrays.copyOfRange(StringConverter.hexStringToArray(INNER1), 6, 16),
                Arrays.copyOfRange(out, 18, 28)); // packet data unchanged
        assertEquals(0x1234, ByteArrayUtils.decodeUnsignedShort(out, 30)); // y shifted by 2
        assertValidPacket(out, 0, out.length, true);
        assertEquals(0, hist.attrs.get("ccsds-seqcount:tc"));
    }

    @Test
    public void testSingleWithoutCrc() throws ErrorInCommand {
        var pp = newPostprocessor(false, new CapturingCommandHistoryPublisher());
        var pc = buildCommand("single", Map.of("x", 5, "tc", INNER1, "y", 0x1234));

        byte[] out = pp.process(pc);
        assertEquals(11 + 1 + 16 + 2 + 2, out.length); // only the outer CRC
        assertEquals(out.length, pp.getBinaryLength(pc));
        assertValidPacket(out, 12, 16, false);
        assertEquals(0x1234, ByteArrayUtils.decodeUnsignedShort(out, 28));
        assertValidPacket(out, 0, out.length, true);
    }

    @Test
    public void testFixedSizeWithCrcFails() throws ErrorInCommand {
        var pp = newPostprocessor(true, new CapturingCommandHistoryPublisher());
        var pc = buildCommand("fixed", Map.of("tc", INNER1));
        assertNull(pp.process(pc));
    }

    @Test
    public void testFixedSizeWithoutCrc() throws ErrorInCommand {
        var pp = newPostprocessor(false, new CapturingCommandHistoryPublisher());
        var pc = buildCommand("fixed", Map.of("tc", INNER1));
        byte[] out = pp.process(pc);
        assertValidPacket(out, 11, 16, false);
    }

    @Test
    public void testNotAnnotatedUnchanged() throws ErrorInCommand {
        var pp = newPostprocessor(true, new CapturingCommandHistoryPublisher());
        var pc = buildCommand("plain", Map.of("data", INNER1));
        byte[] out = pp.process(pc);
        assertEquals(11 + 16 + 2, out.length);
        assertArrayEquals(StringConverter.hexStringToArray(INNER1), Arrays.copyOfRange(out, 11, 27));
    }

    private static Map<String, Object> listArgs() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("n", 2);
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("group_id", 1);
        a1.put("tc", INNER1);
        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("group_id", 2);
        a2.put("tc", INNER2);
        args.put("activities", Arrays.asList(a1, a2));
        return args;
    }

    @Test
    public void testList() throws ErrorInCommand {
        var hist = new CapturingCommandHistoryPublisher();
        var pp = newPostprocessor(true, hist);
        var pc = buildCommand("list", listArgs());

        byte[] out = pp.process(pc);
        assertEquals(11 + 1 + (1 + 18) + (1 + 12) + 2, out.length);
        assertEquals(out.length, pp.getBinaryLength(pc));

        assertEquals(2, out[11]); // n
        assertEquals(1, out[12]); // group id of the first activity
        assertValidPacket(out, 13, 18, true);
        assertEquals(2, out[31]); // group id of the second activity, shifted by the first CRC
        assertValidPacket(out, 32, 12, true);
        assertValidPacket(out, 0, out.length, true);

        // sequence counts follow the order in the packet
        assertEquals(0, seqCount(out, 13));
        assertEquals(1, seqCount(out, 32));
        assertEquals(0, hist.attrs.get("ccsds-seqcount:activities[0].tc"));
        assertEquals(1, hist.attrs.get("ccsds-seqcount:activities[1].tc"));
    }

    @Test
    public void testFindEmbeddedTcs() throws ErrorInCommand {
        var pc = buildCommand("list", listArgs());
        var etcs = PusCommandPostprocessor.findEmbeddedTcs(pc);
        assertEquals(List.of(
                new PusCommandPostprocessor.EmbeddedTc("activities[0].tc", 13, 16, 13 * 8, 0, true),
                new PusCommandPostprocessor.EmbeddedTc("activities[1].tc", 30, 10, 30 * 8, 0, true)), etcs);
    }

    /** records the int attributes published */
    static class CapturingCommandHistoryPublisher implements CommandHistoryPublisher {
        final Map<String, Object> attrs = new HashMap<>();

        @Override
        public void publish(CommandId cmdId, String key, String value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, int value) {
            attrs.put(key, value);
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
}
