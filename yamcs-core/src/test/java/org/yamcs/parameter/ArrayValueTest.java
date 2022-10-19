package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class ArrayValueTest {

    @Test
    public void testFlattenUnflatten2() {
        int[] dim = new int[] { 4, 2 };
        int[] idx = new int[] { 2, 1 };
        int flatidx = ArrayValue.flatIndex(dim, idx);
        assertEquals(5, flatidx);

        int[] idx1 = new int[2];
        ArrayValue.unFlattenIndex(flatidx, dim, idx1);

        assertArrayEquals(idx, idx1);

        int[][] x = new int[3][5];
        x[0] = new int[5];
    }

    @Test
    public void testFlattenUnflatten3() {
        int[] dim = new int[] { 4, 2, 2 };
        int[] idx = new int[] { 1, 1, 0 };
        int flatidx = ArrayValue.flatIndex(dim, idx);
        assertEquals(6, flatidx);

        int[] idx1 = new int[3];
        ArrayValue.unFlattenIndex(flatidx, dim, idx1);

        assertArrayEquals(idx, idx1);
    }

    @Test
    public void testFlattenUnflatten4() {
        int[] dim = new int[] { 4, 2, 2, 3 };
        int[] idx = new int[4];
        for (int i = 0; i < 40; i++) {
            ArrayValue.unFlattenIndex(i, dim, idx);
        }
    }
}
