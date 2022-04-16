package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;

public class CcsdsPacketTest {

    // Non multiple of 16 bytes
    byte[] testPacketOf34Bytes = new byte[] {
            (byte) 0x1b, (byte) 0x8a, (byte) 0xe1, (byte) 0x54, (byte) 0x00, (byte) 0x5b, (byte) 0x46, (byte) 0x7f,
            (byte) 0xb3, (byte) 0x56, (byte) 0x72, (byte) 0x45, (byte) 0x13, (byte) 0x00, (byte) 0xe2, (byte) 0x2b,
            (byte) 0xa1, (byte) 0x92, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x26, (byte) 0x00, (byte) 0x01,
            (byte) 0x00, (byte) 0x90, (byte) 0x00, (byte) 0x30, (byte) 0x07, (byte) 0xe1, (byte) 0x01, (byte) 0x33,
            (byte) 0x00, (byte) 0xec
    };

    // Multiple of 16 bytes
    byte[] testPacketOf32Bytes = new byte[] {
            (byte) 0x1b, (byte) 0x8a, (byte) 0xe1, (byte) 0x54, (byte) 0x00, (byte) 0x5b, (byte) 0x46, (byte) 0x7f,
            (byte) 0xb3, (byte) 0x56, (byte) 0x72, (byte) 0x45, (byte) 0x13, (byte) 0x00, (byte) 0xe2, (byte) 0x2b,
            (byte) 0xa1, (byte) 0x92, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x26, (byte) 0x00, (byte) 0x01,
            (byte) 0x00, (byte) 0x90, (byte) 0x00, (byte) 0x30, (byte) 0x07, (byte) 0xe1, (byte) 0x01, (byte) 0x33,
    };

    @Test
    public void testToString() {
        CcsdsPacket ccsdsPacket = new CcsdsPacket(ByteBuffer.wrap(testPacketOf34Bytes));
        // Initialize TimeEncoding to be able to call CcsdsPacket.toString()
        TimeEncoding.setUp();

        String packetString = ccsdsPacket.toString();
        assertEquals("apid: 906\n" +
                "0000: 1b8a e154 005b 467f b356 7245 1300 e22b ...T.[F\u007F.VrE...+\n" +
                "0010: a192 0300 0026 0001 0090 0030 07e1 0133 .....&.....0...3\n" +
                "0020: 00ec                                    ..              \n", packetString);
    }

    @Test
    public void testToString16() {
        CcsdsPacket ccsdsPacket = new CcsdsPacket(ByteBuffer.wrap(testPacketOf32Bytes));
        // Initialize TimeEncoding to be able to call CcsdsPacket.toString()
        TimeEncoding.setUp();

        String packetString = ccsdsPacket.toString();
        assertEquals("apid: 906\n" +
                "0000: 1b8a e154 005b 467f b356 7245 1300 e22b ...T.[F\u007F.VrE...+\n" +
                "0010: a192 0300 0026 0001 0090 0030 07e1 0133 .....&.....0...3\n", packetString);
    }
}
