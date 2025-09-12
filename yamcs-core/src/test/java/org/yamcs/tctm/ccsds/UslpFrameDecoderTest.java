package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.tctm.ccsds.AosFrameDecoderTest.intToByteArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.python.bouncycastle.util.Arrays;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;
import org.yamcs.security.SdlsSecurityAssociation;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.String;

public class UslpFrameDecoderTest {

    @Test
    public void testTfdzConstructionAllZeros() throws TcTmException {
        byte[] data = intToByteArray(USLP_FRAME_TFDZ_CONSTR_000);
        byte[] expected_tfdz = intToByteArray(TFDZ);

        UslpManagedParameters tmp = getParams(false,false);
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

        UslpManagedParameters tmp = getParams(true, false);
        UslpFrameDecoder ufd = new UslpFrameDecoder(tmp);

        DownlinkTransferFrame tf = ufd.decode(data, 0, data.length);
        assertEquals(0xab, tf.getSpacecraftId());
        assertEquals(1, tf.getVirtualChannelId());
        assertEquals(9, tf.getFirstHeaderPointer());

        assertEquals(tf.getFirstHeaderPointer(), tf.getDataStart());
        byte[] tfdz = Arrays.copyOfRange(data, tf.getDataStart(), tf.getDataEnd());
        assertArrayEquals(expected_tfdz, tfdz);
    }

    @Test
    public void testUslpSecurity() throws TcTmException {
        byte[] data = intToByteArray(USLP_ENCRYPTED);
        byte[] expected_tfdz = intToByteArray(TFDZ_DECRYPTED);

        UslpManagedParameters tmp = getParams(true, true);
        UslpFrameDecoder ufd = new UslpFrameDecoder(tmp);

        DownlinkTransferFrame tf = ufd.decode(data, 0, data.length);
        assertEquals(0xab, tf.getSpacecraftId());
        assertEquals(1, tf.getVirtualChannelId());
        assertEquals(27, tf.getFirstHeaderPointer());

        assertEquals(tf.getFirstHeaderPointer(), tf.getDataStart());
        byte[] tfdz = Arrays.copyOfRange(data, tf.getDataStart(), tf.getDataEnd());
        assertArrayEquals(expected_tfdz, tfdz);
    }


    UslpManagedParameters getParams(boolean variableFrameLength, boolean enableSdls) {
        Map<String, Object> m = new HashMap<>();
        m.put("spacecraftId", 0xab);
        if(variableFrameLength) {
            m.put("minFrameLength", 10);
            m.put("maxFrameLength", 60);
        } else {
            m.put("frameLength", 29);
        }
        m.put("errorDetection", "CRC16");



        Map<String, Object> vc0 = new HashMap<>();
        vc0.put("vcId", 1);
        vc0.put("ocfPresent", true);
        vc0.put("service", "PACKET");
        vc0.put("packetPreprocessorClassName", "org.yamcs.tctm.GenericPacketPreprocessor");

        if(enableSdls){
            int vc_spi = 0;
            vc0.put("encryptionSpi", vc_spi);
            vc0.put("linkName", "TestLink");

            Integer b = 10;
            List<HashMap<String, Object>> encConfig = new ArrayList<>();
            HashMap<String, Object> spi0Config = new HashMap<>();
            spi0Config.put("spi", 0);


            InputStream in = getClass().getResourceAsStream("/sdls/key");
            assertNotNull(in, "File not found!");

            // or as a File/Path
            String path = getClass().getResource("/sdls/key").getPath();

            spi0Config.put("keyFile", path);
            spi0Config.put("seqNumWindow", b);
            spi0Config.put("verifySeqNum", true);
            encConfig.add(spi0Config);
            m.put("encryption", encConfig);
        }
        List<Map<String, Object>> vclist = new ArrayList<>();
        vclist.add(vc0);
        m.put("virtualChannels", vclist);

        YConfiguration config = YConfiguration.wrap(m);
        return new UslpManagedParameters(config);

    }

    int[] TFDZ = {0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA};

    int[] USLP_FRAME_TFDZ_CONSTR_000 = {
        0xC0, 0xA, 0xB8, 0x20, 0x0, 0x1C, 0x81, 0x1, 0x0, 0x0, 0x0, 
        0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 
        0xF5, 0x29,
    };

    int[] USLP_ENCRYPTED = {192, 10, 176, 32, 0, 58, 1, 0, 0, 0, 56, 22, 59, 208, 95, 202, 91, 54, 164, 
        137, 122, 141, 0, 0, 0, 5, 139, 221, 25, 97, 101, 84, 50, 123, 61, 195, 58, 214, 68, 33, 121, 
        37, 58, 214, 125, 93, 26, 41, 135, 173, 210, 151, 165, 253, 61, 87, 6, 22, 110
    };

    int[] TFDZ_DECRYPTED = {84, 104, 105, 115, 32, 105, 115, 32, 97, 32, 116, 101, 115, 116};

    int[] USLP_FRAME_TFDZ_CONSTR_111 = {
        0xC0, 0xA, 0xB8, 0x20, 0x0, 0x1A, 0x81, 0x1, 0xE0, 
        0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 
        0xF5, 0x48,
    };
}
