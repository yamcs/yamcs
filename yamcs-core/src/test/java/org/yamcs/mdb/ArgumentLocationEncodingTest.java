package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.ArgumentLocation;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MetaCommandProcessor.CommandBuildResult;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MetaCommand;

/**
 * Tests that the command encoder records the location of all the argument values
 */
public class ArgumentLocationEncodingTest {

    @BeforeAll
    static void beforeAll() {
        TimeEncoding.setUp();
    }

    @BeforeEach
    public void setup() {
        YConfiguration.setupTest(null);
    }

    private static CommandBuildResult build(Mdb mdb, MetaCommand mc, Map<String, Object> args)
            throws ErrorInCommand {
        var mcp = new MetaCommandProcessor(new ProcessorData("test", mdb, new ProcessorConfig()));
        return mcp.buildCommand(mc, args, 0);
    }

    @Test
    public void testInheritedAndNotByteAligned() throws ErrorInCommand {
        Mdb mdb = MdbFactory.createInstanceByConfig("ArgLocationsTest");
        MetaCommand mc = mdb.getMetaCommand("/ArgLocationsTest/cmd1");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("version", 1);
        args.put("x", 2);
        args.put("y", 0x1234);

        var cbr = build(mdb, mc, args);
        assertEquals(List.of(
                new ArgumentLocation("version", 0, 3),
                new ArgumentLocation("type", 3, 5),
                // 16 bits fixed value in between
                new ArgumentLocation("x", 24, 3),
                new ArgumentLocation("y", 27, 16)),
                cbr.getArgumentLocations());
    }

    @Test
    public void testArrayInArray() throws ErrorInCommand {
        Mdb mdb = MdbFactory.createInstanceByConfig("ArrayInArrayArgCommandTest");
        MetaCommand mc = mdb.getMetaCommand("/ArrayInArrayArgTest/cmd1");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("outer_array_length", "2");
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("inner_array_length", 2);
        a1.put("inner_array", Arrays.asList(0xAB, 0xCD));
        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("inner_array_length", 1);
        a2.put("inner_array", Arrays.asList(0x88));
        args.put("outer_array", Arrays.asList(a1, a2));

        // encoded as 0002 02 00AB 00CD 01 0088
        var cbr = build(mdb, mc, args);
        assertEquals(List.of(
                new ArgumentLocation("outer_array_length", 0, 16),
                new ArgumentLocation("outer_array", 16, 64),
                new ArgumentLocation("outer_array[0]", 16, 40),
                new ArgumentLocation("outer_array[0].inner_array_length", 16, 8),
                new ArgumentLocation("outer_array[0].inner_array", 24, 32),
                new ArgumentLocation("outer_array[0].inner_array[0]", 24, 16),
                new ArgumentLocation("outer_array[0].inner_array[1]", 40, 16),
                new ArgumentLocation("outer_array[1]", 56, 24),
                new ArgumentLocation("outer_array[1].inner_array_length", 56, 8),
                new ArgumentLocation("outer_array[1].inner_array", 64, 16),
                new ArgumentLocation("outer_array[1].inner_array[0]", 64, 16)),
                cbr.getArgumentLocations());
    }

    @Test
    public void testVariableBinary() throws ErrorInCommand {
        Mdb mdb = MdbFactory.createInstanceByConfig("VariableBinaryTest");
        MetaCommand mc = mdb.getMetaCommand("/VariableBinaryTest/Command");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("size", "5");
        args.put("data", "0102030405");
        args.put("value", "3.14");

        var cbr = build(mdb, mc, args);
        assertEquals(List.of(
                new ArgumentLocation("size", 0, 16),
                new ArgumentLocation("data", 16, 40),
                new ArgumentLocation("value", 56, 32)),
                cbr.getArgumentLocations());
        var data = cbr.getArgumentLocations().get(1);
        assertEquals(2, data.byteOffset());
        assertEquals(5, data.byteLength());
    }

    @Test
    public void testTupleRoundTrip() throws ErrorInCommand {
        Mdb mdb = MdbFactory.createInstanceByConfig("ArgLocationsTest");
        MetaCommand mc = mdb.getMetaCommand("/ArgLocationsTest/cmd1");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("version", 1);
        args.put("x", 2);
        args.put("y", 0x1234);
        var cbr = build(mdb, mc, args);

        CommandId cmdId = CommandId.newBuilder().setCommandName(mc.getQualifiedName()).setOrigin("test")
                .setSequenceNumber(1).setGenerationTime(0).build();
        PreparedCommand pc = new PreparedCommand(cmdId);
        pc.setMetaCommand(mc);
        pc.setBinary(cbr.getCmdPacket());
        pc.setArgAssignment(cbr.getArgs(), args.keySet());
        pc.setArgumentLocations(cbr.getArgumentLocations());

        // this is how the command reaches the link (and its post-processor) through the tc stream
        PreparedCommand pc1 = PreparedCommand.fromTuple(pc.toTuple(), mdb);
        assertEquals(cbr.getArgumentLocations(), pc1.getArgumentLocations());
        assertEquals(new ArgumentLocation("y", 27, 16), pc1.getArgumentLocation("y"));
        assertNull(pc1.getArgumentLocation("z"));
    }
}
