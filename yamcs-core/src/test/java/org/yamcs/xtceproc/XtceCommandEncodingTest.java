package org.yamcs.xtceproc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
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

/**
 * Created by msc on 27/05/15.
 */
public class XtceCommandEncodingTest {
    static XtceDb xtcedb;
    static MetaCommandProcessor metaCommandProcessor;
    
    @BeforeClass 
    public static void beforeClass() throws ConfigurationException {        
        TimeEncoding.setUp();
        YConfiguration.setup();
        xtcedb = XtceDbFactory.createInstanceByConfig("refmdb");
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", "test", xtcedb, false));
    }
    
    @Test
    public void intArgTcAbs() throws ErrorInCommand {
        // encode command
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/INT_ARG_TC_ABS");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));
    }

            
    @Test
    public void floatCommand() throws ErrorInCommand {
        // encode command
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("float_arg", "-30");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("double_arg", "1");
        arguments.add(argumentAssignment2);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        assertTrue(b[0] != 0);
    }

    @Test
    public void floatCommandDefault() {
        // encode command
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        boolean errorInCommand = false;

        try {
            // should complain that parameter has not been assigned
            List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>();
           metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        } catch (ErrorInCommand e) {
            errorInCommand = true;
        }

        assertTrue(errorInCommand);
    }

    @Test
    public void stringCommand() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/STRING_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("string_arg", "aaaa");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("terminatedString_arg", "bbbb");
        arguments.add(argumentAssignment2);
        ArgumentAssignment argumentAssignment3 = new ArgumentAssignment("prependedSizeString_arg", "cccc");
        arguments.add(argumentAssignment3);
        ArgumentAssignment argumentAssignment4 = new ArgumentAssignment("fixedString_arg", "dddd");
        arguments.add(argumentAssignment4);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expectedResult = {
                97, 97, 97, 97, 0,              // aaaa
                97, 98, 99, 100, 101, 102, 0,   // abcdef - string2_arg default value
                98, 98, 98, 98, 0x2C,           // bbbb
                0, 4, 99, 99, 99, 99,           // cccc
                100, 100, 100, 100, 0, 0     // dddd
        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }

    @Test
    public void cgsLikeStringCommand() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/CGS_LIKE_STRING_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("string_arg1", "aaaa");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("string_arg2", "bbbb");
        arguments.add(argumentAssignment2);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expectedResult = {
                0, 4, 97, 97, 97, 97, 0, 0,   // aaaa
                98, 98, 98, 98, 0x2C, 0       // bbbb

        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }

    @Test
    public void binaryCommand() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BINARY_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("binary_arg1", "0102");
        arguments.add(argumentAssignment1);
        ArgumentAssignment argumentAssignment2 = new ArgumentAssignment("binary_arg2", "0A1B");
        arguments.add(argumentAssignment2);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        byte[] expectedResult = {
                0x01, 0x02, 0, 0, 0,
                0x0A, 0x1B, 0, 0, 0, 0

        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }
    @Test
    public void littleEndianUint() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/LE_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("p2", "0x12");
        arguments.add(argumentAssignment1);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x0A0B, bb.getShort());
        assertEquals(0x12, bb.getShort());
    }

    @Test
    public void booleanCommandTrue() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("bool_arg1", "true");
        arguments.add(argumentAssignment1);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        assertEquals(0b11000000, b[0]&0xFF);
    }

    @Test
    public void booleanCommandFalseTrue() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("bool_arg1", "false");
        arguments.add(argumentAssignment1);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();

        // - assigned false
        // - default argument assignemnt true
        assertEquals(0b01000000, b[0]&0xFF);


    }

    @Test
    public void int64CommandArgumentRange() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/INT64_ARG_TC");

        try {
            List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060707", "p2", "0xF102030405060708", "p3", "-18374120213919168760");
            metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p1"));
        }


        try {
            List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060708", "p2", "0xF10203040506070A", "p3", "-18374120213919168760");
            metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p2"));
        }

        try {
            List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060708", "p2", "0xF102030405060708", "p3", "-0X0102030405060707");
            metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p3"));
        }
    }

    @Test
    public void int64CommandArgumentEncoding() throws ErrorInCommand {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/INT64_ARG_TC");
        List<ArgumentAssignment> arguments = getArgAssignment("p1", "0X0102030405060708", "p2", "0xF102030405060708", "p3", "-0X0102030405060708");
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        assertEquals("0102030405060708F102030405060708FEFDFCFBFAF9F8F8", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testStringEncodedTc() throws Exception {
        MetaCommand mc = XtceDbFactory.createInstanceByConfig("refmdb").getMetaCommand("/REFMDB/SUBSYS1/STRING_ENCODED_ARG_TC");
        assertNotNull(mc);
        List<ArgumentAssignment> aaList = Arrays.asList(
                new ArgumentAssignment("uint_arg", "1"),
                new ArgumentAssignment("int_arg", "-2"), // with calibration applied
                new ArgumentAssignment("float_arg", "-3.01"),
                new ArgumentAssignment("string_arg", "string with \n special chars \""),
                new ArgumentAssignment("binary_arg", "010A"),
                new ArgumentAssignment("enumerated_arg", "value1"),
                new ArgumentAssignment("boolean_arg", "false"));
        ErrorInCommand e = null;

        byte[] b = metaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();
        String result = new String(b);
        String expected = "1,-3,-3.01,string with \n special chars \",010A,1,false,";

        assertEquals(expected, result);
    }
    @Test
    public void testCustomCalibTc() throws Exception {
        MetaCommand mc = XtceDbFactory.createInstanceByConfig("refmdb").getMetaCommand("/REFMDB/SUBSYS1/CUSTOM_CALIB_TC");
        List<ArgumentAssignment> arguments = Arrays.asList(
                new ArgumentAssignment("p1", "10"),
                new ArgumentAssignment("p2", "20.08553692318766774092"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        assertEquals("0000000D0003", StringConverter.arrayToHexString(b));
    }
    private List<ArgumentAssignment> getArgAssignment(String ...v) {
        if((v.length&0x1)!=0) throw new IllegalArgumentException("Please pass an even number of arguments: arg1,value1,arg2,value2...");
        List<ArgumentAssignment> arguments = new LinkedList<ArgumentAssignment>() ;
        for(int i=0;i<v.length;i+=2) {
            ArgumentAssignment arg = new ArgumentAssignment(v[i], v[i+1]);
            arguments.add(arg);
        }
        return arguments;
    }
    
    @Test
    public void testOneIntArg() throws Exception {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC");
        assertNotNull(mc);
        byte[] b= metaCommandProcessor.buildCommand(mc, new ArrayList<ArgumentAssignment>()).getCmdPacket();
        assertEquals("ABCDEFAB", StringConverter.arrayToHexString(b));

    }

    @Test
    public void testFixedValue() throws Exception {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/FIXED_VALUE_TC");
        assertNotNull(mc);
        byte[] b= metaCommandProcessor.buildCommand(mc, new ArrayList<ArgumentAssignment>()).getCmdPacket();
        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));
    }
    @Test
    public void testIntegerArg() throws Exception {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/INT_ARG_TC");
        assertNotNull(mc);
        byte[] b= metaCommandProcessor.buildCommand(mc, new ArrayList<ArgumentAssignment>()).getCmdPacket();
        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));

    }

    @Test
    public void testFloatArg() throws Exception {

        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("float_arg", "-10.23"),
                new ArgumentAssignment("double_arg", "25.4"));


        byte[] b= metaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();

        assertEquals(16, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);

        assertEquals(-10.23, bb.getFloat(), 1e-5);

        assertEquals(25.4d, bb.getDouble(), 1e-20);
    }

    @Test
    public void testLittleEndianFloatArg() throws Exception {

        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/LE_FLOAT_INT_ARG_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("float_arg", "1.0"),
                new ArgumentAssignment("uint_arg1", "2"),
                new ArgumentAssignment("uint_arg2", "3")
                );


        byte[] b= metaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();
        assertEquals(12, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(1.0, bb.getFloat(), 1e-5);
        assertEquals(2, bb.getInt());
        assertEquals(3, bb.getInt());
    }
    
    @Test
    public void testCcsdsTc() throws Exception {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("uint8_arg", "1"),
                new ArgumentAssignment("uint16_arg", "2"),
                new ArgumentAssignment("int32_arg", "-3"),
                new ArgumentAssignment("uint64_arg", "4"));

        byte[] b= metaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();
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
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        assertNotNull(mc);
        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("uint8_arg", "5"),
                new ArgumentAssignment("uint16_arg", "2"),
                new ArgumentAssignment("int32_arg", "-3"),
                new ArgumentAssignment("uint64_arg", "4"));
        ErrorInCommand e = null;
        try {
            metaCommandProcessor.buildCommand(mc, aaList);
        } catch (ErrorInCommand e1) {
            e=e1;
        }               
        assertNotNull(e);
        assertTrue(e.getMessage().contains("not in the range"));
    }

    @Test
    public void testCalibration() throws Exception {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("p1", "1"),
                new ArgumentAssignment("p2", "1"),
                new ArgumentAssignment("p3", "-3.2"),
                new ArgumentAssignment("p4", "value2"));

        byte[] b= metaCommandProcessor.buildCommand(mc, aaList).getCmdPacket();
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
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");
        assertNotNull(mc);

        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("p1", "1"),
                new ArgumentAssignment("p2", "1"),
                new ArgumentAssignment("p3", "-3.2"),
                new ArgumentAssignment("p4", "invalidenum"));

        ErrorInCommand e = null;
        try {
            metaCommandProcessor.buildCommand(mc, aaList);
        } catch (ErrorInCommand e1) {
            e=e1;
        }
        assertNotNull(e);
        assertTrue(e.getMessage().contains("Cannot assign value to p4"));
    }

    @Test(expected = ErrorInCommand.class)
    public void testExceptionOnReassigningInheritanceArgument() throws Exception {
        MetaCommand mc = xtcedb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        List<ArgumentAssignment> assignments = Arrays.asList(new ArgumentAssignment("uint8_arg", "2"),
                new ArgumentAssignment("uint16_arg", "2"),
                new ArgumentAssignment("int32_arg", "2"),
                new ArgumentAssignment("uint64_arg", "2"),
                new ArgumentAssignment("ccsds-apid", "123")); // Already assigned by parent
        metaCommandProcessor.buildCommand(mc, assignments);
    }
}
