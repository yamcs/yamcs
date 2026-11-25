package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.LongToIntFunction;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;

public class DataTypeProcessorTest {

    private static final long[][] UNSIGNED_TESTS = {
            { 1, 1 },
            { 2, 2 },
            { 255, 8 },
            { 256, 9 },
            { 0xFFFFFFFFL, 32 },
            { 0x100000000L, 33 },
            { 0xFFFFFFFFFFFFFFFFL, 64 }
    };

    private static final long[][] SIGNED_TESTS = {
            { 0x7FFFFFFFFFFFFFFFL, 64 },
            { 0x3FFFFFFFFFFFFFFFL, 63 },
            { 0x1FFFFFFFFFFFFFFFL, 62 },
            { 0x00000000000000FF, 9 },
            { 0x0000000000000003, 3 },
            { 0x0000000000000001, 2 },
            { 0x0000000000000000, 1 },

            { -1, 1 },
            { -2, 2 },
            { -3, 3 },
            { -4, 3 },
            { -127, 8 },
            { -128, 8 },
            { -129, 9 },
            { -32768, 16 },
            { -32769, 17 },
            { Long.MIN_VALUE, 64 },
    };
    @Test
    public void testUnsignedSizeInBits() {
        runTests("DataTypeProcessor unsigned", UNSIGNED_TESTS,
                v -> DataTypeProcessor.unsignedSizeInBits(v));
    }

    @Test
    public void testSignedSizeInBits() {
        runTests("DataTypeProcessor signed", SIGNED_TESTS,
                v -> DataTypeProcessor.signedSizeInBits(v));
    }

    @Test
    public void testUint32ToUnsigned32() {
        Value v = DataTypeProcessor.convertEngValueForType(intType(32, false), ValueUtility.getUint32Value(-1));
        assertEquals(Type.UINT32, v.getType());
        assertEquals(0xFFFFFFFFL, Integer.toUnsignedLong(v.getUint32Value()));
    }

    @Test
    public void testUint32ToSigned64() {
        Value v = DataTypeProcessor.convertEngValueForType(intType(64, true), ValueUtility.getUint32Value(-1));
        assertEquals(Type.SINT64, v.getType());
        assertEquals(0xFFFFFFFFL, v.getSint64Value());
    }

    @Test
    public void testUint32ToSigned32OutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> DataTypeProcessor.convertEngValueForType(intType(32, true), ValueUtility.getUint32Value(-1)));
    }

    @Test
    public void testUint32ToUnsigned8() {
        Value v = DataTypeProcessor.convertEngValueForType(intType(8, false), ValueUtility.getUint32Value(255));
        assertEquals(255, v.getUint32Value());
        assertThrows(IllegalArgumentException.class,
                () -> DataTypeProcessor.convertEngValueForType(intType(8, false), ValueUtility.getUint32Value(256)));
    }

    @Test
    public void testUint32ToSigned8() {
        Value v = DataTypeProcessor.convertEngValueForType(intType(8, true), ValueUtility.getUint32Value(127));
        assertEquals(127, v.getSint32Value());
        assertThrows(IllegalArgumentException.class,
                () -> DataTypeProcessor.convertEngValueForType(intType(8, true), ValueUtility.getUint32Value(128)));
    }

    @Test
    public void testUint64ToUnsigned64() {
        Value v = DataTypeProcessor.convertEngValueForType(intType(64, false), ValueUtility.getUint64Value(-1L));
        assertEquals(Type.UINT64, v.getType());
        assertEquals(-1L, v.getUint64Value());
    }

    @Test
    public void testUint64ToSigned64() {
        Value v = DataTypeProcessor.convertEngValueForType(intType(64, true),
                ValueUtility.getUint64Value(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, v.getSint64Value());
        assertThrows(IllegalArgumentException.class,
                () -> DataTypeProcessor.convertEngValueForType(intType(64, true),
                        ValueUtility.getUint64Value(Long.MIN_VALUE)));
    }

    @Test
    public void testUint64ToUnsigned8() {
        assertThrows(IllegalArgumentException.class,
                () -> DataTypeProcessor.convertEngValueForType(intType(8, false), ValueUtility.getUint64Value(256)));
    }

    @Test
    public void testUint32ToFloat64() {
        var type = new FloatParameterType.Builder().setName("f64").setSizeInBits(64).build();
        Value v = DataTypeProcessor.convertEngValueForType(type, ValueUtility.getUint32Value(-1));
        assertEquals(Type.DOUBLE, v.getType());
        assertEquals(4294967295.0, v.getDoubleValue());
    }

    private static IntegerParameterType intType(int sizeInBits, boolean signed) {
        return new IntegerParameterType.Builder()
                .setName((signed ? "s" : "u") + sizeInBits)
                .setSigned(signed)
                .setSizeInBits(sizeInBits)
                .build();
    }

    private void runTests(String caption, long[][] tests, LongToIntFunction func) {
        int failures = 0;
        for (int i = 0; i < tests.length; ++i) {
            long value = tests[i][0];
            int expected = (int) tests[i][1];
            if (!checkResult(caption, expected, value, func)) {
                ++failures;
            }
        }

        if (failures > 0) {
            fail(String.format("%d failures for %s tests", failures, caption));
        }
    }

    private boolean checkResult(String caption, int expected, long v, LongToIntFunction func) {
        int actual = func.applyAsInt(v);
        if (expected == actual) {
            return true;
        } else {
            System.out.println(String.format("%s: v=%d (%016X), expected %d but was %d",
                    caption, v, v, expected, actual));
            return false;
        }
    }

}
