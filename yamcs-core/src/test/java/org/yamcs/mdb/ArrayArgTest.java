package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.xml.XtceLoadException;

/**
 * Tests that a command containing an array argument
 */
public class ArrayArgTest {

    private Mdb mdb;
    private MetaCommandProcessor metaCommandProcessor;

    @BeforeEach
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        mdb = MdbFactory
                .createInstanceByConfig("ArrayArgCommandTest");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @Test
    public void testCommandEncoding() throws ErrorInCommand, IOException {
        MetaCommand mc = mdb.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("length", "5");
        args.put("array1", "[1,2,3,4,5]");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("00050102030405", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testCommandEncodingAutomaticLength() throws ErrorInCommand, IOException {
        MetaCommand mc = mdb.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("array1", "[1,2,3,4,5]");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("00050102030405", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testCommandEncodingInvalidLength() throws IOException {
        MetaCommand mc = mdb.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("length", "3");// length does not match the array length
        args.put("array1", "[1,2,3,4,5]");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args);
        });
    }

    @Test
    public void testMaxLengthExceeded() throws ErrorInCommand, IOException {
        MetaCommand mc = mdb.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("array1", "[1,2,3,4,5,6]");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args);
        });
    }

    @Test
    public void testNativeArrayArgument() throws ErrorInCommand, IOException {
        MetaCommand mc = mdb.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("length", 5);
        args.put("array1", Arrays.asList(1, 2, 3, 4, 5));
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("00050102030405", StringConverter.arrayToHexString(b));
    }
}
