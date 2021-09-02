package org.yamcs.xtceproc;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.xml.XtceLoadException;

/**
 * Tests that a command containing an array argument
 */
public class ArrayArgTest {

    private XtceDb db;
    private MetaCommandProcessor metaCommandProcessor;

    @Before
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        db = XtceDbFactory
                .createInstanceByConfig("ArrayArgCommandTest");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", "test", db, new ProcessorConfig()));
    }

    @Test
    public void testCommandEncoding() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("length", "5");
        args.put("array1", "[1,2,3,4,5]");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("00050102030405", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testCommandEncodingAutomaticLength() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("array1", "[1,2,3,4,5]");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("00050102030405", StringConverter.arrayToHexString(b));
    }

    @Test(expected = ErrorInCommand.class)
    public void testCommandEncodingInvalidLength() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("length", "3");// length does not match the array length
        args.put("array1", "[1,2,3,4,5]");
        metaCommandProcessor.buildCommand(mc, args);
    }

    @Test(expected = ErrorInCommand.class)
    public void testMaxLengthExceeded() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("array1", "[1,2,3,4,5,6]");
        metaCommandProcessor.buildCommand(mc, args);
    }

    @Test
    public void testNativeArrayArgument() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/ArrayArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("length", 5);
        args.put("array1", Arrays.asList(1, 2, 3, 4, 5));
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("00050102030405", StringConverter.arrayToHexString(b));
    }
}
