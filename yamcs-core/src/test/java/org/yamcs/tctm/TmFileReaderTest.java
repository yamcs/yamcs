package org.yamcs.tctm;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.orekit.errors.OrekitException;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.TmFileReader;

import static org.junit.Assert.*;


import org.yamcs.utils.TimeEncoding;


public class TmFileReaderTest {
	
	@BeforeClass
	public static void beforeClass() throws OrekitException {
		TimeEncoding.setUp();
	}
	
	@Test
	public void testRawCcsdsReader() throws InterruptedException, IOException {
		TmFileReader tfr=new TmFileReader("src/test/resources/TmFileReaderTest-rawccsds");
		PacketWithTime pwrt=tfr.readPacket();
		assertNotNull(pwrt);
		assertEquals(148, pwrt.bb.capacity());
		assertEquals(0x1be5d9a0, pwrt.bb.getInt(0));
		
		
		pwrt=tfr.readPacket();
		assertNotNull(pwrt);
		assertEquals(528, pwrt.bb.capacity());
		assertEquals(0x1bdff44c, pwrt.bb.getInt(0));
		assertEquals(0x1, pwrt.bb.getInt(520));
		
		pwrt=tfr.readPacket();
		assertNull(pwrt);
	}
	
	
	@Test(expected=IOException.class)
	public void testHrdpReader() throws InterruptedException, IOException {
		TmFileReader tfr=new TmFileReader("src/test/resources/TmFileReaderTest-hrdp-corrupted");
		PacketWithTime pwrt=tfr.readPacket();
		assertNotNull(pwrt);
		assertEquals(148, pwrt.bb.capacity());
		assertEquals(0x1be5d9a0, pwrt.bb.getInt(0));
		
		
		pwrt=tfr.readPacket();
		assertNotNull(pwrt);
		assertEquals(528, pwrt.bb.capacity());
		assertEquals(0x1bdff44c, pwrt.bb.getInt(0));
		assertEquals(0x1, pwrt.bb.getInt(520));
		
		pwrt=tfr.readPacket();
	}
}
