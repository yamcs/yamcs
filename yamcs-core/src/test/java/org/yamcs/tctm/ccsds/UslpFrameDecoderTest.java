package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.yamcs.tctm.ccsds.AosFrameDecoderTest.intToByteArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.python.bouncycastle.util.Arrays;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;

public class UslpFrameDecoderTest {

    @Test
    public void testTfdzConstructionAllZeros() throws TcTmException {
        byte[] data = intToByteArray(USLP_FRAME_TFDZ_CONSTR_000);
        byte[] expected_tfdz = intToByteArray(TFDZ);

        UslpManagedParameters tmp = getParams(false);
        UslpFrameDecoder ufd = new UslpFrameDecoder(tmp);

        DownlinkTransferFrame tf = ufd.decode(data, 0, data.length);
        assertEquals(0xab, tf.getSpacecraftId());
        assertEquals(1, tf.getVirtualChannelId());
        assertEquals(11, tf.getDataStart());

        assertEquals(tf.getFirstHeaderPointer(), tf.getDataStart());
        byte[] tfdz = Arrays.copyOfRange(data, tf.getDataStart(), tf.getDataEnd());
        assertArrayEquals(expected_tfdz, tfdz);
    }

    @Test
    public void testTfdzConstructionAllOnes() throws TcTmException {
        byte[] data = intToByteArray(USLP_FRAME_TFDZ_CONSTR_111);
        byte[] expected_tfdz = intToByteArray(TFDZ);

        UslpManagedParameters tmp = getParams(true);
        UslpFrameDecoder ufd = new UslpFrameDecoder(tmp);

        DownlinkTransferFrame tf = ufd.decode(data, 0, data.length);
        assertEquals(0xab, tf.getSpacecraftId());
        assertEquals(1, tf.getVirtualChannelId());
        assertEquals(9, tf.getFirstHeaderPointer());

        assertEquals(tf.getFirstHeaderPointer(), tf.getDataStart());
        byte[] tfdz = Arrays.copyOfRange(data, tf.getDataStart(), tf.getDataEnd());
        assertArrayEquals(expected_tfdz, tfdz);
    }


    UslpManagedParameters getParams(boolean variableFrameLength) {
        Map<String, Object> m = new HashMap<>();
        m.put("spacecraftId", 0xab);
        if(variableFrameLength) {
            m.put("minFrameLength", 10);
            m.put("maxFrameLength", 30);
        } else {
            m.put("frameLength", 29);
        }
        m.put("errorDetection", "CRC16");

        List<Map<String, Object>> vclist = new ArrayList<>();
        m.put("virtualChannels", vclist);

        Map<String, Object> vc0 = new HashMap<>();
        vc0.put("vcId", 1);
        vc0.put("ocfPresent", true);
        vc0.put("service", "PACKET");
        vc0.put("packetPreprocessorClassName", "org.yamcs.tctm.GenericPacketPreprocessor");

        vclist.add(vc0);

        YConfiguration config = YConfiguration.wrap(m);
        return new UslpManagedParameters(config, null, null);

    }

    int[] TFDZ = {0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA};

    int[] USLP_FRAME_TFDZ_CONSTR_000 = {
        0xC0, 0xA, 0xB8, 0x20, 0x0, 0x1C, 0x81, 0x1, 0x0, 0x0, 0x0, 
        0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 
        0xF5, 0x29,
    };

    int[] USLP_FRAME_TFDZ_CONSTR_111 = {
        0xC0, 0xA, 0xB8, 0x20, 0x0, 0x1A, 0x81, 0x1, 0xE0, 
        0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 
        0xF5, 0x48,
    };
}