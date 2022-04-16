package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;

public class UsocPacketInputStreamTest {

    @BeforeAll
    public static void beforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    public void testRawCcsdsReader() throws InterruptedException, IOException, PacketTooLongException {
        UsocPacketInputStream tfr = new UsocPacketInputStream();
        tfr.init(new FileInputStream("src/test/resources/TmFileReaderTest-rawccsds"), YConfiguration.emptyConfig());
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

    @Test
    public void testHrdpReader() throws InterruptedException, IOException, PacketTooLongException {
        assertThrows(IOException.class, () -> {
            UsocPacketInputStream tfr = new UsocPacketInputStream();
            tfr.init(new FileInputStream("src/test/resources/TmFileReaderTest-hrdp-corrupted"),
                    YConfiguration.emptyConfig());
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
        });
    }
}
