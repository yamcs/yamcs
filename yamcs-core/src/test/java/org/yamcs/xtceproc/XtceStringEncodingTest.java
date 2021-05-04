package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

public class XtceStringEncodingTest {
    static XtceDb mdb;
    static MetaCommandProcessor metaCommandProcessor;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeClass
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = XtceDbFactory.createInstanceByConfig("xtce-strings-cmd");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", "test", mdb, new ProcessorConfig()));
    }

    @Before
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    // null terminated string in fixed size buffer
    public void testFixedSizeString1() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command1");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string1", "abc"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = { 'a', 'b', 'c', 0, 0, 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // null terminated string in fixed size buffer but the string is as long as the buffer so there is no terminator
    public void testFixedSizeString1_noterminator() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command1");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string1", "abcdef"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // fixed size string in fixed size buffer
    public void testFixedSizeString2() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command2");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string2", "abcdef"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // null terminated string in undefined buffer
    public void testFixedSizeString3() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command3");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string3", "ab"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // null terminated string in undefined buffer - max size
    public void testFixedSizeString3_max() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command3");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string3", "abcde"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 'c', 'd', 'e', 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test(expected = ErrorInCommand.class)
    // null terminated string in undefined buffer exceeding the size
    public void testFixedSizeString3_too_long() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command3");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string3", "abcdef"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
    }

    @Test
    // prefixed size string in buffer whose size is given by another argument
    public void testFixedSizeString4() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command4");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("buf_length", "6"));
        arguments.add(new ArgumentAssignment("string4", "abc"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expected = new byte[] {
                0x00, 0x06, // buffer size
                0x03, // string size
                'a', 'b', 'c', // string
                0, 0, // filler at the end of the buffer
                0x01, 0x02 // uint16_param1 coming after the string
        };
        assertArrayEquals(expected, b);
    }

    @Test(expected = ErrorInCommand.class)
    // prefixed size string in buffer whose size is given by another argument which is too long
    public void testFixedSizeString4_too_long() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command4");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("buf_length", "7"));
        arguments.add(new ArgumentAssignment("string4", "ab"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
    }

    @Test(expected = ErrorInCommand.class)
    // too long prefixed size string in buffer whose size is given by another argument
    public void testFixedSizeString4_too_long2() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command4");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("buf_length", "4"));
        arguments.add(new ArgumentAssignment("string4", "abcd"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
    }

    @Test
    // prefixed size string in undefined buffer
    public void testFixedSizeString5() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command5");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string5", "ab"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expected = new byte[] { 0x00, 0x02, 'a', 'b', 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test(expected = ErrorInCommand.class)
    // prefixed size string in undefined buffer exceeding max size
    public void testFixedSizeString5_too_long() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command5");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string5", "abcde"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expected = new byte[] { 0x00, 0x02, 'a', 'b', 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    public void testStringEncodedAsBinary() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command6");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("string6", "ab"));
        arguments.add(new ArgumentAssignment("para1", "258"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expected = new byte[] { 'a', 'b', 0, 0, 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

}
