package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;

public class XtceBooleansTest {
    static Mdb mdb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;
    MetaCommandProcessor metaCommandProcessor;
    static ProcessorData pdata;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("xtce-booleans");
        pdata = new ProcessorData("test", mdb, new ProcessorConfig());
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        metaCommandProcessor = new MetaCommandProcessor(pdata);
    }

    @Test
    public void testNumericParaFalse() {
        byte[] buf = new byte[] { 0 };
        ContainerProcessingResult cpr = processPacket(buf, mdb.getSequenceContainer("/Booleans/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool1"));
        assertEquals(false, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testNumericParaTrue() {
        byte[] buf = new byte[] { 5 };
        ContainerProcessingResult cpr = processPacket(buf, mdb.getSequenceContainer("/Booleans/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool1"));
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testStringParaTrue() {
        byte[] buf = new byte[] { 'Y', 'e', 's', '!' };
        ContainerProcessingResult cpr = processPacket(buf, mdb.getSequenceContainer("/Booleans/packet2"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool2"));
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testStringParaFalse() {
        byte[] buf = new byte[] { 'N', 'o', 'o', 'o' };
        ContainerProcessingResult cpr = processPacket(buf, mdb.getSequenceContainer("/Booleans/packet2"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool2"));
        assertEquals(false, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testNumericCmdTrue() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
        Map<String, Object> args = new HashMap<>();

        args.put("bool1", "True");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = { 1 };
        assertArrayEquals(expected, b);
    }

    @Test
    @Disabled("Test to be enabled only when BooleanDataType deprecated handling is removed")
    public void testNumericCmdTrueCaseSensitive() {
        assertThrows(ErrorInCommand.class, () -> {
            MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
            Map<String, Object> args = new HashMap<>();

            args.put("bool1", "true");
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    public void testNumericCmdFalse() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
        Map<String, Object> args = new HashMap<>();

        args.put("bool1", "False");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = { 0 };
        assertArrayEquals(expected, b);
    }

    @Test
    @Disabled("Test to be enabled only when BooleanDataType deprecated handling is removed")
    public void testNumericCmdFalseCaseSensitive() {
        assertThrows(ErrorInCommand.class, () -> {
            MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
            Map<String, Object> args = new HashMap<>();

            args.put("bool1", "false");
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    public void testStringCmdTrue() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command2");
        Map<String, Object> args = new HashMap<>();

        args.put("bool2", "yes!");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = { 'y', 'e', 's', '!' };
        assertArrayEquals(expected, b);
    }

    @Test
    public void testStringCmdFalse() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command2");
        Map<String, Object> args = new HashMap<>();

        args.put("bool2", "nooo");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = { 'n', 'o', 'o', 'o' };
        assertArrayEquals(expected, b);
    }

    @Test
    public void testNativeTrueArgument() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
        Map<String, Object> args = new HashMap<>();

        args.put("bool1", true);
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = { 1 };
        assertArrayEquals(expected, b);
    }

    @Test
    public void testNativeFalseArgument() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
        Map<String, Object> args = new HashMap<>();

        args.put("bool1", false);
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = { 0 };
        assertArrayEquals(expected, b);
    }

    private Parameter param(String name) {
        return mdb.getParameter("/Booleans/" + name);
    }

    private ContainerProcessingResult processPacket(byte[] buf, SequenceContainer sc) {
        return extractor.processPacket(buf, now, now, 0, sc);
    }
}
