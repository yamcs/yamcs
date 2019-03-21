package org.yamcs.xtce;

import static org.junit.Assert.*;

import org.junit.Test;

public class PathElementTest {
    @Test
    public void test1() {
        PathElement pe = PathElement.fromString("a[3][4]");
        assertEquals("a", pe.getName());
        assertArrayEquals(new int[] {3, 4}, pe.index);
    }
    
    @Test
    public void test2() {
        PathElement pe = PathElement.fromString("[3]");
        assertEquals(null, pe.getName());
        assertArrayEquals(new int[] {3}, pe.index);
    }
    
    @Test
    public void test3() {
        PathElement pe = PathElement.fromString("xyz");
        assertEquals("xyz", pe.getName());
        assertArrayEquals(null, pe.index);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void test4() {
        PathElement pe = PathElement.fromString("xyz[");
        assertEquals(null, pe.getName());
        assertArrayEquals(new int[] {3}, pe.index);
    }
}
