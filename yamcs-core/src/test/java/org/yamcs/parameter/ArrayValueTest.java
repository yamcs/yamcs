package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ArrayValueTest {

    @Test
    public void testFlattenUnflatten2() {
        int[] dim = new int[] { 4, 2 };
        int[] idx = new int[] { 1, 2 };
        int flatidx = ArrayValue.flatIndex(dim, idx);
        assertEquals(6, flatidx);

        int[] idx1 = new int[2];
        ArrayValue.unFlattenIndex(flatidx, dim, idx1);

        assertArrayEquals(idx, idx1);
    }

    @Test
    public void testFlattenUnflatten3() {
        int[] dim = new int[] { 4, 2, 2 };
        int[] idx = new int[] { 0, 3, 0 };
        int flatidx = ArrayValue.flatIndex(dim, idx);
        assertEquals(6, flatidx);

        int[] idx1 = new int[3];
        ArrayValue.unFlattenIndex(flatidx, dim, idx1);

        assertArrayEquals(idx, idx1);
    }
}
