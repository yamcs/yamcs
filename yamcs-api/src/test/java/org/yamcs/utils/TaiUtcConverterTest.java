package org.yamcs.utils;


import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;

public class TaiUtcConverterTest {
	@BeforeClass
	static public void setUp() {
		TimeEncoding.setUp();
	}
	
	@Test
	public void test0() throws Exception {
		TaiUtcConverter tuc = new TaiUtcConverter();
		DateTimeComponents dtc = tuc.instantToUtc(0);
		assertEquals(1969, dtc.year);
		assertEquals(12, dtc.month);
		assertEquals(31, dtc.day);
		assertEquals(23, dtc.hour);
		assertEquals(59, dtc.minute);
		assertEquals(51, dtc.second);
		
		
	}
	
	@Test
	public void test2008() throws Exception {
		TaiUtcConverter tuc = new TaiUtcConverter();
		DateTimeComponents dtc = tuc.instantToUtc(1230768032000L);
		assertEquals(59, dtc.second);
		
	}
}
