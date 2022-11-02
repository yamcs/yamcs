package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;

public class AosFrameDecoderTest {

    @Test
    public void testFrame01() throws TcTmException {
        byte[] data = intToByteArray(AOS_FRAME_01);

        AosManagedParameters tmp = getParams();
        AosFrameDecoder tfd = new AosFrameDecoder(tmp);

        AosTransferFrame tf = tfd.decode(data, 0, data.length);
        assertEquals(0xAB, tf.getSpacecraftId());
        assertEquals(1, tf.getVirtualChannelId());
        assertEquals(343, tf.getVcFrameSeq());

        assertEquals(8, tf.getFirstHeaderPointer());

        assertFalse(tf.hasOcf());

        List<byte[]> pktList = new ArrayList<>();
        PacketDecoder pd = new PacketDecoder(500, (byte[] p) -> pktList.add(p));

        pd.process(tf.getData(), tf.getFirstHeaderPointer(), tf.getDataEnd() - tf.getFirstHeaderPointer());
        assertTrue(pd.hasIncompletePacket());

        assertTrue(pktList.isEmpty());
    }

    @Test
    public void testCorruptedFrame01() {
        assertThrows(TcTmException.class, () -> {
            byte[] data = intToByteArray(AOS_FRAME_01);
            data[10] = 12; // change a byte to break the crc

            AosManagedParameters tmp = getParams();
            AosFrameDecoder tfd = new AosFrameDecoder(tmp);

            tfd.decode(data, 0, data.length);
        });
    }

    AosManagedParameters getParams() {
        Map<String, Object> m = new HashMap<>();
        m.put("spacecraftId", 3);
        m.put("frameLength", 128);
        m.put("errorDetection", "CRC16");
        m.put("insertZoneLength", 0);
        m.put("frameHeaderErrorControlPresent", false);

        List<Map<String, Object>> vclist = new ArrayList<>();
        m.put("virtualChannels", vclist);

        Map<String, Object> vc0 = new HashMap<>();
        vc0.put("vcId", 0);
        vc0.put("ocfPresent", false);
        vc0.put("service", "PACKET");
        vc0.put("packetPreprocessorClassName", "org.yamcs.tctm.GenericPacketPreprocessor");

        Map<String, Object> vc1 = new HashMap<>();
        vc1.put("vcId", 1);
        vc1.put("ocfPresent", false);
        vc1.put("service", "PACKET");
        vc1.put("packetPreprocessorClassName", "org.yamcs.tctm.GenericPacketPreprocessor");

        vclist.add(vc0);
        vclist.add(vc1);

        YConfiguration config = YConfiguration.wrap(m);
        return new AosManagedParameters(config);

    }

    public static byte[] intToByteArray(int[] b) {
        byte[] data = new byte[b.length];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) b[i];
        }
        return data;
    }

    // copied from channel-emulator unit tests
    // https://github.com/nasa/channel-emulator/blob/master/test/aos_frame_test.cpp
    int[] AOS_FRAME_01 = {
            0x6a, 0xc1, 0x00, 0x01, 0x57, 0x00, 0x00, 0x00, 0xea, 0x00, 0x01, 0x8d, 0x21, 0x45, 0x00, 0x01,
            0x88, 0x00, 0x00, 0x40, 0x00, 0x3f, 0x11, 0x8c, 0xc3, 0xc0, 0xa8, 0x64, 0x28, 0xc0, 0xa8, 0xc8,
            0x28, 0x75, 0x30, 0x75, 0x31, 0x01, 0x74, 0x00, 0x00, 0x88, 0x88, 0x88, 0x88, 0x88, 0x88, 0x88,
            0x77, 0x88, 0x88, 0x88, 0x77, 0x88, 0x88, 0x88, 0x88, 0x88, 0x77, 0x88, 0x88, 0x88, 0x88, 0x77,
            0x77, 0x55, 0x44, 0x44, 0x44, 0x44, 0x33, 0x44, 0x33, 0x44, 0x44, 0x55, 0x55, 0x66, 0x55, 0x88,
            0x88, 0x88, 0x88, 0x88, 0x88, 0x77, 0x66, 0x77, 0x66, 0x66, 0x77, 0x66, 0x77, 0x66, 0x66, 0x66,
            0x55, 0x55, 0x66, 0x66, 0x66, 0x77, 0x88, 0x88, 0x88, 0x77, 0x88, 0x88, 0x77, 0x77, 0x77, 0x77,
            0x77, 0x66, 0x77, 0x77, 0x77, 0x77, 0x77, 0x88, 0x88, 0x88, 0x88, 0x77, 0x88, 0x77, 0x93, 0xfd
    };
}
