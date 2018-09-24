package org.yamcs.xtce;

import static org.junit.Assert.*;

import org.junit.Test;

public class PathElementTest {
    
    @Test
    public void test1() {
        PathElement pe = PathElement.fromString("aa[1][2][3]");
        assertEquals("aa", pe.name);
        assertArrayEquals(new int[] {1, 2, 3}, pe.index);
        
    }
    
    @Test
    public void test2() {
        PathElement pe = PathElement.fromString("abc");
        assertEquals("abc", pe.name);
        assertNull(pe.index);
    }
}
