package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ArrayDataTypeTest {
    IntegerParameterType uint32 = new IntegerParameterType.Builder().setSizeInBits(31).build();
    ArrayParameterType type2d = new ArrayParameterType.Builder()
            .setNumberOfDimensions(2)
            .setElementType(uint32)
            .build();

    @Test
    public void test1() {
        assertThrows(IllegalArgumentException.class, () -> {
            type2d.convertType("[1, 2,3]");
        });
    }

    @Test
    public void test2() {
        Object[] o = (Object[]) type2d.convertType("[[1, 2], [3, 4]]");
        Long[][] arr = { { 1L, 2L }, { 3L, 4L } };
        assertArrayEquals(arr, o);
    }
}
