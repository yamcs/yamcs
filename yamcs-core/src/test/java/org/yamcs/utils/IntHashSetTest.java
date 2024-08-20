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

    @Test
    public void testRemoveExistingElement() {
        IntHashSet set = new IntHashSet();
        set.add(10);

        assertTrue(set.contains(10));
        assertTrue(set.remove(10));
        assertFalse(set.contains(10));
        assertEquals(0, set.size());
    }

    @Test
    public void testRemoveNonExistingElement() {
        IntHashSet set = new IntHashSet();
        set.add(10);
        assertFalse(set.remove(20));
        assertTrue(set.contains(10));
        assertEquals(1, set.size());
    }

    @Test
    public void testRemoveEmptyValue() {
        IntHashSet set = new IntHashSet();
        set.add(Integer.MIN_VALUE);
        assertTrue(set.contains(Integer.MIN_VALUE));
        assertTrue(set.remove(Integer.MIN_VALUE));
        assertFalse(set.contains(Integer.MIN_VALUE));
        assertEquals(0, set.size());
    }

    @Test
    public void testRemoveAlreadyRemovedElement() {
        IntHashSet set = new IntHashSet();
        set.add(10);
        assertTrue(set.remove(10));
        assertFalse(set.remove(10));
        assertFalse(set.contains(10));
        assertEquals(0, set.size());
    }

    @Test
    public void testRemoveFromEmptySet() {
        IntHashSet set = new IntHashSet();

        assertFalse(set.remove(10));
        assertFalse(set.contains(10));
        assertEquals(0, set.size());
    }

    @Test
    public void testRemoveUpdatesSize() {
        IntHashSet set = new IntHashSet();

        set.add(10);
        set.add(20);
        set.add(30);
        assertEquals(3, set.size());

        assertTrue(set.remove(20));
        assertEquals(2, set.size());

        assertTrue(set.remove(10));
        assertEquals(1, set.size());

        assertTrue(set.remove(30));
        assertEquals(0, set.size());
    }

    @Test
    public void testRemoveAndRehash() {
        IntHashSet set = new IntHashSet();

        set.add(10);
        set.add(20);
        set.add(30);

        assertTrue(set.remove(20));
        assertTrue(set.add(40)); // Should add successfully even after removal

        assertTrue(set.contains(10));
        assertTrue(set.contains(30));
        assertTrue(set.contains(40));
        assertFalse(set.contains(20));
    }
}
