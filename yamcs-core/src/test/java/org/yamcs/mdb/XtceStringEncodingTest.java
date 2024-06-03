package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MetaCommand;

public class XtceStringEncodingTest {
    static Mdb mdb;
    static MetaCommandProcessor metaCommandProcessor;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("xtce-strings-cmd");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    // null terminated string in fixed size buffer
    public void testFixedSizeString1() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command1");
        Map<String, Object> args = new HashMap<>();

        args.put("string1", "abc");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = { 'a', 'b', 'c', 0, 0, 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // null terminated string in fixed size buffer but the string is as long as the buffer so there is no terminator
    public void testFixedSizeString1_noterminator() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command1");
        Map<String, Object> args = new HashMap<>();

        args.put("string1", "abcdef");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // fixed size string in fixed size buffer
    public void testFixedSizeString2() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command2");
        Map<String, Object> args = new HashMap<>();

        args.put("string2", "abcdef");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // null terminated string in undefined buffer
    public void testFixedSizeString3() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command3");
        Map<String, Object> args = new HashMap<>();

        args.put("string3", "ab");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // null terminated string in undefined buffer - max size
    public void testFixedSizeString3_max() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command3");
        Map<String, Object> args = new HashMap<>();

        args.put("string3", "abcde");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = new byte[] { 'a', 'b', 'c', 'd', 'e', 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // null terminated string in undefined buffer exceeding the size
    public void testFixedSizeString3_too_long() {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command3");
        Map<String, Object> args = new HashMap<>();

        args.put("string3", "abcdef");
        args.put("para1", "258");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    // prefixed size string in buffer whose size is given by another argument
    public void testFixedSizeString4() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command4");
        Map<String, Object> args = new HashMap<>();

        args.put("buf_length", "6");
        args.put("string4", "abc");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        byte[] expected = new byte[] {
                0x00, 0x06, // buffer size
                0x03, // string size
                'a', 'b', 'c', // string
                0, 0, // filler at the end of the buffer
                0x01, 0x02 // uint16_param1 coming after the string
        };
        assertArrayEquals(expected, b);
    }

    @Test
    // prefixed size string in buffer whose size is given by another argument which is too long
    public void testFixedSizeString4_too_long() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command4");
        Map<String, Object> args = new HashMap<>();

        args.put("buf_length", "7");
        args.put("string4", "ab");
        args.put("para1", "258");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    // too long prefixed size string in buffer whose size is given by another argument
    public void testFixedSizeString4_too_long2() {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command4");
        Map<String, Object> args = new HashMap<>();

        args.put("buf_length", "4");
        args.put("string4", "abcd");
        args.put("para1", "258");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    // prefixed size string in undefined buffer
    public void testFixedSizeString5() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command5");
        Map<String, Object> args = new HashMap<>();

        args.put("string5", "ab");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        byte[] expected = new byte[] { 0x00, 0x02, 'a', 'b', 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

    @Test
    // prefixed size string in undefined buffer exceeding max size
    public void testFixedSizeString5_too_long() {
        assertThrows(ErrorInCommand.class, () -> {
            MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command5");
            Map<String, Object> args = new HashMap<>();

            args.put("string5", "abcde");
            args.put("para1", "258");
            byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

            byte[] expected = new byte[] { 0x00, 0x02, 'a', 'b', 0x01, 0x02 };
            assertArrayEquals(expected, b);
        });
    }

    @Test
    public void testStringEncodedAsBinary() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/StringsCmd/command6");
        Map<String, Object> args = new HashMap<>();

        args.put("string6", "ab");
        args.put("para1", "258");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        byte[] expected = new byte[] { 'a', 'b', 0, 0, 0, 0x01, 0x02 };
        assertArrayEquals(expected, b);
    }

}
