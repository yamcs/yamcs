package org.yamcs.tctm;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.tctm.UsocPacketInputStream;

import static org.junit.Assert.*;

import org.yamcs.utils.TimeEncoding;

public class UsocPacketInputStreamTest {

    @BeforeClass
    public static void beforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    public void testRawCcsdsReader() throws InterruptedException, IOException, PacketTooLongException {
        UsocPacketInputStream tfr = new UsocPacketInputStream(new FileInputStream("src/test/resources/TmFileReaderTest-rawccsds"));
        byte[] packet = tfr.readPacket();

        assertNotNull(packet);
        ByteBuffer bb = ByteBuffer.wrap(packet);
        assertEquals(148, bb.capacity());
        assertEquals(0x1be5d9a0, bb.getInt(0));

        packet = tfr.readPacket();
        assertNotNull(packet);
        bb = ByteBuffer.wrap(packet);
        assertEquals(528, bb.capacity());
        assertEquals(0x1bdff44c, bb.getInt(0));
        assertEquals(0x1, bb.getInt(520));

        packet = tfr.readPacket();
        assertNull(packet);
        
        tfr.close();
    }

    @Test(expected = IOException.class)
    public void testHrdpReader() throws InterruptedException, IOException, PacketTooLongException {
        UsocPacketInputStream tfr = new UsocPacketInputStream(new FileInputStream("src/test/resources/TmFileReaderTest-hrdp-corrupted"));
        byte[] packet = tfr.readPacket();
        assertNotNull(packet);
        ByteBuffer bb = ByteBuffer.wrap(packet);
        assertEquals(148, bb.capacity());
        assertEquals(0x1be5d9a0, bb.getInt(0));

        packet = tfr.readPacket();
        assertNotNull(packet);
        bb = ByteBuffer.wrap(packet);
        assertEquals(528, bb.capacity());
        assertEquals(0x1bdff44c, bb.getInt(0));
        assertEquals(0x1, bb.getInt(520));

        packet = tfr.readPacket();
    }
}
