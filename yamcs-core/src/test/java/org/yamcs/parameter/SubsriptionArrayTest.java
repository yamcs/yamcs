package org.yamcs.parameter;

import static org.junit.Assert.*;

import org.junit.Test;

public class SubsriptionArrayTest {

    @Test
    public void test() {
	SubscriptionArray s = new SubscriptionArray();
	s.add(1);
	assertArrayEquals(new int[] {1}, s.getArray());
	
	s.add(1);
	assertEquals(1, s.size());
	
	s.add(3);
	assertEquals(2, s.size());
	s.add(4);
	
	s.add(2);
	assertArrayEquals(new int[] {1, 2, 3, 4}, s.getArray());
	
	assertTrue(s.remove(3));
	assertArrayEquals(new int[] {1, 2, 4}, s.getArray());
	
	assertFalse(s.remove(3));
	assertArrayEquals(new int[] {1, 2, 4}, s.getArray());
    }

}
