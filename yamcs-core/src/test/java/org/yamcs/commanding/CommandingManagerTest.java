package org.yamcs.commanding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.YConfiguration;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.MetaCommandProcessor;
import org.yamcs.xtceproc.XtceDbFactory;

public class CommandingManagerTest {
    static XtceDb xtceDb;

    @BeforeClass 
    public static void beforeClass() throws ConfigurationException {        
        TimeEncoding.setUp();
        YConfiguration.setup();
        xtceDb = XtceDbFactory.createInstance("refmdb");
    }
            
    @Test
    public void testOneIntArg() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC");
        assertNotNull(mc);
        byte[] b= MetaCommandProcessor.buildCommand(mc, new ArrayList<ArgumentAssignment>()).getCmdPacket();
        assertEquals("ABCDEFAB", StringConverter.arrayToHexString(b));

    }

    @Test
    public void testFixedValue() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/FIXED_VALUE_TC");
        assertNotNull(mc);
        byte[] b= MetaCommandProcessor.buildCommand(mc, new ArrayList<ArgumentAssignment>()).getCmdPacket();
        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));

    }
    @Test
    public void testIntegerArg() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/INT_ARG_TC");
        assertNotNull(mc);
        byte[] b= MetaCommandProcessor.buildCommand(mc, new ArrayList<ArgumentAssignment>()).getCmdPacket();
        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));

    }

    @Test
    public void testFloatArg() throws Exception {

        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("float_arg", "-10.23"),
                new ArgumentAssignment("double_arg", "25.4"));


        byte[] b= MetaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();

        assertEquals(16, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);

        assertEquals(-10.23, bb.getFloat(), 1e-5);

        assertEquals(25.4d, bb.getDouble(), 1e-20);
    }

    @Test
    public void testCcsdsTc() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("uint8_arg", "1"),
                new ArgumentAssignment("uint16_arg", "2"),
                new ArgumentAssignment("int32_arg", "-3"),
                new ArgumentAssignment("uint64_arg", "4"));

        byte[] b= MetaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();
        assertEquals(31, b.length);

        CcsdsPacket p = new CcsdsPacket(b);

        assertEquals(100, p.getAPID());
        assertEquals(0xABCDEFAB, p.getPacketID());
        assertEquals(1, p.getTimeId());
        assertEquals(true, p.getChecksumIndicator());

        ByteBuffer bb = ByteBuffer.wrap(b);
        assertEquals(1, bb.get(16));
        assertEquals(2, bb.getShort(17));
        assertEquals(-3, bb.getInt(19));
        assertEquals(4, bb.getLong(23));


    }

    @Test
    public void testValidIntegerRange() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        assertNotNull(mc);
        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("uint8_arg", "5"),
                new ArgumentAssignment("uint16_arg", "2"),
                new ArgumentAssignment("int32_arg", "-3"),
                new ArgumentAssignment("uint64_arg", "4"));
        ErrorInCommand e = null;
        try {
            MetaCommandProcessor.buildCommand(mc, aaList);
        } catch (ErrorInCommand e1) {
            e=e1;
        }		
        assertNotNull(e);
        assertTrue(e.getMessage().contains("not in the range"));
    }

    @Test
    public void testCalibration() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("p1", "1"),
                new ArgumentAssignment("p2", "1"),
                new ArgumentAssignment("p3", "-3.2"),
                new ArgumentAssignment("p4", "value2"));

        byte[] b= MetaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();
        assertEquals(9, b.length);

        ByteBuffer bb = ByteBuffer.wrap(b);

        assertEquals(3, bb.getShort(0));
        assertEquals(2, bb.getShort(2));
        assertEquals(-5.4, bb.getFloat(4), 1e-5);

        int p4 = (bb.get(8)&0xFF)>>6;

        assertEquals(2, p4);
    }

    @Test
    public void testInvalidEnum() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("p1", "1"),
                new ArgumentAssignment("p2", "1"),
                new ArgumentAssignment("p3", "-3.2"),
                new ArgumentAssignment("p4", "invalidenum"));

        ErrorInCommand e = null;
        try {
            MetaCommandProcessor.buildCommand(mc, aaList);
        } catch (ErrorInCommand e1) {
            e=e1;
        }
        assertNotNull(e);
        assertTrue(e.getMessage().contains("Cannot assign value to p4"));
    }

    @Test(expected = ErrorInCommand.class)
    public void testExceptionOnReassigningInheritanceArgument() throws Exception {
        MetaCommand mc = xtceDb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        List<ArgumentAssignment> assignments = Arrays.asList(new ArgumentAssignment("uint8_arg", "2"),
                new ArgumentAssignment("uint16_arg", "2"),
                new ArgumentAssignment("int32_arg", "2"),
                new ArgumentAssignment("uint64_arg", "2"),
                new ArgumentAssignment("ccsds-apid", "123")); // Already assigned by parent
        MetaCommandProcessor.buildCommand(mc, assignments);
    }
}
