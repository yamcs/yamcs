package org.yamcs.client;

import static org.junit.Assert.*;

import java.net.URISyntaxException;

import org.junit.Test;
import org.yamcs.api.atermis.YamcsConnectData;



public class YamcsConnectDataTest {
	@Test
	public void testParsing() throws URISyntaxException {
		YamcsConnectData ycd=YamcsConnectData.parse("yamcs://localhost");
		assertEquals("localhost",ycd.host);
		assertEquals(5445,ycd.port);
		assertNull(ycd.instance);
	}
	
	@Test
	public void testParsing1() throws URISyntaxException {
		YamcsConnectData ycd=YamcsConnectData.parse("yamcs://localhost:3321/test");
		assertEquals("localhost",ycd.host);
		assertEquals(3321,ycd.port);
		assertEquals("test",ycd.instance);
	}
	
	@Test
    public void testParsingInVM() throws URISyntaxException {
        YamcsConnectData ycd=YamcsConnectData.parse("yamcs:///test/");
        assertEquals(null, ycd.host);
        assertEquals("test", ycd.instance);
    }
	
	@Test(expected=URISyntaxException.class)
	public void testParsing3() throws URISyntaxException {
		YamcsConnectData ycd=YamcsConnectData.parse("yamcs://addre ss.com:33/test");
	}
}
