package org.yamcs.xtce;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ArrayDataTypeTest {
    IntegerParameterType uint32 = new IntegerParameterType.Builder().setSizeInBits(31).build();
    ArrayParameterType type2d = new ArrayParameterType.Builder()
            .setNumberOfDimensions(2)
            .setElementType(uint32)
            .build();

    int[][] arr = { { 1, 2 }, { 3, 4, 5 } };

    @Test(expected = IllegalArgumentException.class)
    public void test1() {
        type2d.convertType("[1, 2,3]");
    }

    @Test
    public void test2() {
        Object[] o = (Object[]) type2d.convertType("[[1, 2], [3, 4]]");
        long[][] arr = { { 1, 2 }, { 3, 4 } };
        assertArrayEquals(arr, o);
    }
}
