package org.yamcs.xtceproc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

public class XtceBooleansTest {
    static XtceDb mdb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;
    MetaCommandProcessor metaCommandProcessor;
    static ProcessorData pdata;

    @BeforeClass
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = XtceDbFactory.createInstanceByConfig("xtce-booleans");
        pdata = new ProcessorData("test", "test", mdb, new ProcessorConfig());
    }

    @Before
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        metaCommandProcessor = new MetaCommandProcessor(pdata);
    }

    @Test
    public void testNumericParaFalse() {
        byte[] buf = new byte[] { 0 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                mdb.getSequenceContainer("/Booleans/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool1"));
        assertEquals(false, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testNumericParaTrue() {
        byte[] buf = new byte[] { 5 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                mdb.getSequenceContainer("/Booleans/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool1"));
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testStringParaTrue() {
        byte[] buf = new byte[] { 'Y', 'e', 's', '!' };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                mdb.getSequenceContainer("/Booleans/packet2"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool2"));
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testStringParaFalse() {
        byte[] buf = new byte[] { 'N', 'o', 'o', 'o' };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                mdb.getSequenceContainer("/Booleans/packet2"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("bool2"));
        assertEquals(false, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testNumericCmdTrue() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("bool1", "true"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = { 1 };
        assertArrayEquals(expected, b);
    }

    @Test
    public void testNumericCmdFalse() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command1");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("bool1", "false"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = { 0 };
        assertArrayEquals(expected, b);
    }

    @Test
    public void testStringCmdTrue() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command2");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("bool2", "yes!"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = { 'y', 'e', 's', '!' };
        assertArrayEquals(expected, b);
    }

    @Test
    public void testStringCmdFalse() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/Booleans/command2");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        arguments.add(new ArgumentAssignment("bool2", "nooo"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        byte[] expected = { 'n', 'o', 'o', 'o' };
        assertArrayEquals(expected, b);
    }

    private Parameter param(String name) {
        return mdb.getParameter("/Booleans/" + name);
    }
}
