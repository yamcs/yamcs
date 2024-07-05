package org.yamcs.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntHashSetTest {

    @Test
    public void testAddAndContains() {
        IntHashSet set = new IntHashSet();

        assertTrue(set.add(1));
        assertTrue(set.add(2));
        assertTrue(set.add(3));

        assertTrue(set.contains(1));
        assertTrue(set.contains(2));
        assertTrue(set.contains(3));
        assertFalse(set.contains(4));
    }

    @Test
    public void testDuplicateAdd() {
        IntHashSet set = new IntHashSet();

        assertTrue(set.add(1));
        assertFalse(set.add(1));
    }

    @Test
    public void testMinValue() {
        IntHashSet set = new IntHashSet();

        assertTrue(set.add(Integer.MIN_VALUE));
        assertTrue(set.contains(Integer.MIN_VALUE));

        assertFalse(set.add(Integer.MIN_VALUE));
    }

    @Test
    public void testRehashing() {
        IntHashSet set = new IntHashSet();

        for (int i = 0; i < 100; i++) {
            assertTrue(set.add(i));
        }

        for (int i = 0; i < 100; i++) {
            assertTrue(set.contains(i));
        }

        assertFalse(set.contains(100));
    }
}
