package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;

import org.junit.jupiter.api.Test;

public class BooleanArrayTest {

    @Test
    public void test1() {
        BooleanArray ba = new BooleanArray();

        ba.add(false);
        assertFalse(ba.get(0));
        ba.add(true);
        assertFalse(ba.get(0));
        assertTrue(ba.get(1));

        ba.add(0, true);
        assertEquals(3, ba.size());
        assertTrue(ba.get(0));
        assertFalse(ba.get(1));
        assertTrue(ba.get(2));

        for (int i = 0; i < 100; i++) {
            ba.add(0, true);
        }
        assertEquals(103, ba.size());
        assertTrue(ba.get(0));
        assertTrue(ba.get(100));
        assertFalse(ba.get(101));
        assertTrue(ba.get(102));

    }

    @Test
    public void test2() {
        BooleanArray ba = new BooleanArray();
        BitSet bitSet = new BitSet();
        for (int i = 0; i < 100; i++) {
            ba.add(3 * i, false);
            ba.add(3 * i + 1, true);
            ba.add(3 * i + 2, true);

            bitSet.set(3 * i, false);
            bitSet.set(3 * i + 1, true);
            bitSet.set(3 * i + 2, true);
        }
        assertArrayEquals(bitSet.toLongArray(), ba.toLongArray());
    }
}
