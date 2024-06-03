package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.python.google.common.collect.ImmutableMap;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.MetaCommandProcessor.CommandBuildResult;
import org.yamcs.parameter.Value;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.MetaCommand;

public class RefMdbCommandEncodingTest {
    static Mdb mdb;
    static MetaCommandProcessor metaCommandProcessor;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("refmdb");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @Test
    public void intArgTcAbs() throws ErrorInCommand {
        // encode command
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/INT_ARG_TC_ABS");
        Map<String, Object> args = new HashMap<>();
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));
    }

    @Test
    public void floatCommand() throws ErrorInCommand {
        // encode command
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("float_arg", "-30");
        args.put("double_arg", "1");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertTrue(b[0] != 0);
    }

    @Test
    public void nativeFloatCommand() throws ErrorInCommand {
        // encode command
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("float_arg", -30);
        args.put("double_arg", 1);
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertTrue(b[0] != 0);
    }

    @Test
    public void floatCommandDefault() {
        // encode command
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        boolean errorInCommand = false;

        try {
            // should complain that parameter has not been assigned
            Map<String, Object> args = new HashMap<>();
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        } catch (ErrorInCommand e) {
            errorInCommand = true;
        }

        assertTrue(errorInCommand);
    }

    @Test
    public void stringCommand() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/STRING_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("string_arg", "aaaa");
        args.put("terminatedString_arg", "bbbb");
        args.put("prependedSizeString_arg", "cccc");
        args.put("fixedString_arg", "dddd");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        byte[] expectedResult = {
                97, 97, 97, 97, 0, // aaaa
                97, 98, 99, 100, 101, 102, 0, // abcdef - string2_arg default value
                98, 98, 98, 98, 0x2C, // bbbb
                0, 4, 99, 99, 99, 99, // cccc
                100, 100, 100, 100, 0, 0 // dddd
        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }

    @Test
    public void cgsLikeStringCommand() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/CGS_LIKE_STRING_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("string_arg1", "aaaa");
        args.put("string_arg2", "bbbb");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        byte[] expectedResult = {
                0, 4, 97, 97, 97, 97, 0, 0, // aaaa
                98, 98, 98, 98, 0x2C, 0 // bbbb

        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }

    @Test
    public void binaryCommand() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/BINARY_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("binary_arg1", "0102");
        args.put("binary_arg2", "0A1B");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        byte[] expectedResult = {
                0x01, 0x02, 0, 0, 0,
                0x0A, 0x1B, 0, 0, 0, 0

        };
        assertEquals(StringConverter.arrayToHexString(expectedResult), StringConverter.arrayToHexString(b));
    }

    @Test
    public void littleEndianUint() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/LE_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("p2", "0x12");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x0A0B, bb.getShort());
        assertEquals(0x12, bb.getShort());
    }

    @Test
    public void booleanCommandTrue() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("bool_arg1", BooleanDataType.DEFAULT_ONE_STRING_VALUE);
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals(0b11000000, b[0] & 0xFF);
    }

    @Test
    public void booleanCommandFalseTrue() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/BOOLEAN_ARG_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("bool_arg1", BooleanDataType.DEFAULT_ZERO_STRING_VALUE);
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        // - assigned false
        // - default argument assignemnt true
        assertEquals(0b01000000, b[0] & 0xFF);
    }

    @Test
    public void int64CommandArgumentRange() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/INT64_ARG_TC");

        try {
            Map<String, Object> args = getArgAssignment("p1", "0X0102030405060707", "p2",
                    "0xF102030405060708", "p3", "-18374120213919168760");
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p1"));
        }

        try {
            Map<String, Object> args = getArgAssignment("p1", "0X0102030405060708", "p2",
                    "0xF10203040506070A", "p3", "-18374120213919168760");
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p2"));
        }

        try {
            Map<String, Object> args = getArgAssignment("p1", "0X0102030405060708", "p2",
                    "0xF102030405060708", "p3", "-0X0102030405060707");
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
            fail("Should throw an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Cannot assign value to p3"));
        }
    }

    @Test
    public void int64CommandArgumentEncoding() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/INT64_ARG_TC");
        Map<String, Object> args = getArgAssignment("p1", "0X0102030405060708", "p2", "0xF102030405060708",
                "p3", "-0X0102030405060708");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals("0102030405060708F102030405060708FEFDFCFBFAF9F8F8", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testStringEncodedTc() throws Exception {
        MetaCommand mc = MdbFactory.createInstanceByConfig("refmdb")
                .getMetaCommand("/REFMDB/SUBSYS1/STRING_ENCODED_ARG_TC");
        assertNotNull(mc);
        Map<String, Object> args = new HashMap<>();
        args.put("uint_arg", "1");
        args.put("int_arg", "-2"); // with calibration applied
        args.put("float_arg", "-3.01");
        args.put("string_arg", "string with \n special chars \"");
        args.put("binary_arg", "010A");
        args.put("enumerated_arg", "value1");
        args.put("boolean_arg", "False");

        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        String result = new String(b);
        String expected = "1,-3,-3.01,string with \n special chars \",010A,1,False,";

        assertEquals(expected, result);
    }

    @Test
    public void testCustomCalibTc() throws Exception {
        MetaCommand mc = MdbFactory.createInstanceByConfig("refmdb")
                .getMetaCommand("/REFMDB/SUBSYS1/CUSTOM_CALIB_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("p1", "10");
        args.put("p2", "20.08553692318766774092");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals("0000000D0003", StringConverter.arrayToHexString(b));
    }

    private Map<String, Object> getArgAssignment(String... v) {
        if ((v.length & 0x1) != 0) {
            throw new IllegalArgumentException("Please pass an even number of arguments: arg1,value1,arg2,value2...");
        }
        Map<String, Object> args = new HashMap<>();
        for (int i = 0; i < v.length; i += 2) {
            args.put(v[i], v[i + 1]);
        }
        return args;
    }

    @Test
    public void testOneIntArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC");
        assertNotNull(mc);
        byte[] b = metaCommandProcessor.buildCommand(mc, new HashMap<>()).getCmdPacket();
        assertEquals("ABCDEFAB", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testFixedValue() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/FIXED_VALUE_TC");
        assertNotNull(mc);
        byte[] b = metaCommandProcessor.buildCommand(mc, new HashMap<>()).getCmdPacket();
        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testIntegerArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/INT_ARG_TC");
        assertNotNull(mc);
        byte[] b = metaCommandProcessor.buildCommand(mc, new HashMap<>()).getCmdPacket();
        assertEquals("ABCD901408081808", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testFloatArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC");
        assertNotNull(mc);

        Map<String, Object> args = new HashMap<>();
        args.put("float_arg", "-10.23");
        args.put("double_arg", "25.4");

        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals(16, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);

        assertEquals(-10.23, bb.getFloat(), 1e-5);

        assertEquals(25.4d, bb.getDouble(), 1e-20);
    }

    @Test
    public void testLittleEndianFloatArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/LE_FLOAT_INT_ARG_TC");
        assertNotNull(mc);

        Map<String, Object> args = new HashMap<>();
        args.put("float_arg", "1.0");
        args.put("uint_arg1", "2");
        args.put("uint_arg2", "3");

        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(12, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(1.0, bb.getFloat(), 1e-5);
        assertEquals(2, bb.getInt());
        assertEquals(3, bb.getInt());
    }

    @Test
    public void testCcsdsTc() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        assertNotNull(mc);

        Map<String, Object> args = new HashMap<>();
        args.put("uint8_arg", "1");
        args.put("uint16_arg", "2");
        args.put("int32_arg", "-3");
        args.put("uint64_arg", "4");

        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(31, b.length);

        CcsdsPacket p = new CcsdsPacket(b);

        assertEquals(100, p.getAPID());
        assertEquals(0xABCDEFAB, ByteArrayUtils.decodeInt(b, 12));
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
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        assertNotNull(mc);
        Map<String, Object> args = new HashMap<>();
        args.put("uint8_arg", "5");
        args.put("uint16_arg", "2");
        args.put("int32_arg", "-3");
        args.put("uint64_arg", "4");
        ErrorInCommand e = null;
        try {
            metaCommandProcessor.buildCommand(mc, args);
        } catch (ErrorInCommand e1) {
            e = e1;
        }
        assertNotNull(e);
        assertTrue(e.getMessage().contains("not in the range"));
    }

    @Test
    public void testCalibration() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");
        assertNotNull(mc);

        Map<String, Object> args = new HashMap<>();
        args.put("p1", "1");
        args.put("p2", "1");
        args.put("p3", "-3.2");
        args.put("p4", "value2");

        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(9, b.length);

        ByteBuffer bb = ByteBuffer.wrap(b);

        assertEquals(3, bb.getShort(0));
        assertEquals(2, bb.getShort(2));
        assertEquals(-5.4, bb.getFloat(4), 1e-5);

        int p4 = (bb.get(8) & 0xFF) >> 6;

        assertEquals(2, p4);
    }

    @Test
    public void testInvalidEnum() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/CALIB_TC");
        assertNotNull(mc);

        Map<String, Object> args = new HashMap<>();
        args.put("p1", "1");
        args.put("p2", "1");
        args.put("p3", "-3.2");
        args.put("p4", "invalidenum");

        ErrorInCommand e = null;
        try {
            metaCommandProcessor.buildCommand(mc, args);
        } catch (ErrorInCommand e1) {
            e = e1;
        }
        assertNotNull(e);
        assertTrue(e.getMessage().contains("Cannot assign value to p4"));
    }

    @Test
    public void testExceptionOnReassigningInheritanceArgument() {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/CCSDS_TC");
        Map<String, Object> args = new HashMap<>();
        args.put("uint8_arg", "2");
        args.put("uint16_arg", "2");
        args.put("int32_arg", "2");
        args.put("uint64_arg", "2");
        args.put("ccsds-apid", "123"); // Already assigned by parent
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args);
        });
    }

    @Test
    public void testTimeArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/TIME_ARG_TC");
        assertNotNull(mc);
        String tstring = "2020-01-01T00:00:00.123Z";
        long tlong = TimeEncoding.parse(tstring);

        Map<String, Object> args = new HashMap<>();
        args.put("t1", tstring);
        CommandBuildResult cbr = metaCommandProcessor.buildCommand(mc, args);
        Value v1 = cbr.args.get(mc.getArgument("t1")).getEngValue();

        assertEquals(tlong, v1.getTimestampValue());
        byte[] cmdb = cbr.getCmdPacket();
        assertEquals(4, cmdb.length);
        int gpsTime = ByteArrayUtils.decodeInt(cmdb, 0);
        assertEquals(TimeEncoding.toGpsTimeMillisec(tlong) / 1000, gpsTime);
    }

    @Test
    public void testAggregateArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/AGGR_TC");
        assertNotNull(mc);

        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "{member1: 3, member2: 'value2'}");
        CommandBuildResult cbr = metaCommandProcessor.buildCommand(mc, args);

        byte[] cmdb = cbr.getCmdPacket();
        assertEquals("0380", StringConverter.arrayToHexString(cmdb));
    }

    @Test
    public void testNativeAggregateArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/REFMDB/SUBSYS1/AGGR_TC");
        assertNotNull(mc);

        Map<String, Object> args = new HashMap<>();
        args.put("arg1", ImmutableMap.of("member1", 3, "member2", "value2"));
        CommandBuildResult cbr = metaCommandProcessor.buildCommand(mc, args);

        byte[] cmdb = cbr.getCmdPacket();
        assertEquals("0380", StringConverter.arrayToHexString(cmdb));
    }
}
