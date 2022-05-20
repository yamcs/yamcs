package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.PathElement;

public class PathElementTest {
    @Test
    public void test1() {
        PathElement pe = PathElement.fromString("a[3][4]");
        assertEquals("a", pe.getName());
        assertArrayEquals(new int[] { 3, 4 }, pe.getIndex());
    }

    @Test
    public void test2() {
        PathElement pe = PathElement.fromString("[3]");
        assertEquals(null, pe.getName());
        assertArrayEquals(new int[] { 3 }, pe.getIndex());
    }

    @Test
    public void test3() {
        PathElement pe = PathElement.fromString("xyz");
        assertEquals("xyz", pe.getName());
        assertArrayEquals(null, pe.getIndex());
    }

    @Test
    public void test4() {
        assertThrows(IllegalArgumentException.class, () -> {
            PathElement pe = PathElement.fromString("xyz[");
            assertEquals(null, pe.getName());
            assertArrayEquals(new int[] { 3 }, pe.getIndex());
        });
    }
}
