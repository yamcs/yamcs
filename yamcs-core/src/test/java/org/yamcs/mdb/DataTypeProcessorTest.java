package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.LongToIntFunction;

import org.junit.jupiter.api.Test;

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
