package org.yamcs.tctm;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.TmFileReader;

import static org.junit.Assert.*;


import org.yamcs.utils.TimeEncoding;


public class TmFileReaderTest {
	
	@BeforeClass
	public static void beforeClass() {
		TimeEncoding.setUp();
	}
	
	@Test
	public void testRawCcsdsReader() throws InterruptedException, IOException {
		TmFileReader tfr=new TmFileReader("src/test/resources/TmFileReaderTest-rawccsds");
		PacketWithTime pwrt=tfr.readPacket(TimeEncoding.getWallclockTime());
		
		assertNotNull(pwrt);
		ByteBuffer bb = ByteBuffer.wrap(pwrt.getPacket());
		assertEquals(148, bb.capacity());
		assertEquals(0x1be5d9a0, bb.getInt(0));
		
		
		pwrt=tfr.readPacket(TimeEncoding.getWallclockTime());
		assertNotNull(pwrt);
		bb = ByteBuffer.wrap(pwrt.getPacket());
		assertEquals(528, bb.capacity());
		assertEquals(0x1bdff44c, bb.getInt(0));
		assertEquals(0x1, bb.getInt(520));
		
		pwrt=tfr.readPacket(TimeEncoding.getWallclockTime());
		assertNull(pwrt);
	}
	
	
	@Test(expected=IOException.class)
	public void testHrdpReader() throws InterruptedException, IOException {
		TmFileReader tfr=new TmFileReader("src/test/resources/TmFileReaderTest-hrdp-corrupted");
		PacketWithTime pwrt=tfr.readPacket(TimeEncoding.getWallclockTime());
		assertNotNull(pwrt);
		ByteBuffer bb = ByteBuffer.wrap(pwrt.getPacket());
		assertEquals(148, bb.capacity());
		assertEquals(0x1be5d9a0, bb.getInt(0));
		
		
		pwrt=tfr.readPacket(TimeEncoding.getWallclockTime());
		assertNotNull(pwrt);
		bb = ByteBuffer.wrap(pwrt.getPacket());
		assertEquals(528, bb.capacity());
		assertEquals(0x1bdff44c, bb.getInt(0));
		assertEquals(0x1, bb.getInt(520));
		
		pwrt=tfr.readPacket(TimeEncoding.getWallclockTime());
	}
}
