package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.rs.ReedSolomon;

public class CcsdsFrameDecoderTest {
    CcsdsFrameDecoder decoder;
    ReedSolomon rs;
    Random random = new Random();

    @BeforeEach
    public void setUp() {
        Map<String, Object> m = new HashMap<>();
        m.put("codec", "RS");
        m.put("errorCorrectionCapability", 16);
        m.put("interleavingDepth", 5);
        m.put("derandomize", false);
        YConfiguration config = YConfiguration.wrap(m);
        decoder = new CcsdsFrameDecoder(config);
        rs = new ReedSolomon(2 * 16, 8, 112, 11, 0x187, 0);
    }

    @Test
    public void testDecoderInitialization() {
        assertEquals(5, decoder.interleavingDepth);
        assertEquals(5 * 255, decoder.encodedFrameLength());
        assertEquals(5 * 223, decoder.decodedFrameLength());
    }

    @Test
    public void testDecodeFrameWithValidData() {
        byte[] data = new byte[1115];
        random.nextBytes(data);

        byte[] encoded = encodeFrame(5, data);
        assertEquals(1275, encoded.length);

        // corrupt the data to the maximum correctable extent
        for (int i = 0; i < 5 * 16; i++) {
            encoded[i] = 0;
        }


        int decodedLength = decoder.decodeFrame(encoded, 0, encoded.length);
        assertEquals(1115, decodedLength);

        assertArrayEquals(data, Arrays.copyOfRange(encoded, 0, decodedLength));
    }

    @Test
    public void testDecodeFrameWithUncorrectableData() {
        byte[] data = new byte[1115];
        random.nextBytes(data);

        byte[] encoded = encodeFrame(5, data);
        assertEquals(1275, encoded.length);

        // make the data uncorrectable
        for (int i = 0; i < 5 * 17; i++) {
            encoded[i] = 0;
        }

        int decodedLength = decoder.decodeFrame(encoded, 0, encoded.length);
        assertEquals(-1, decodedLength);

    }

    byte[] encodeFrame(int interleavingDepth, byte[] data) {
        byte[] encoded = new byte[1275];
        byte[] d = new byte[223];

        for (int i = 0; i < interleavingDepth; i++) {
            for (int j = 0; j < d.length; j++) {
                d[j] = data[interleavingDepth * j + i];
            }
            byte[] parity = new byte[32];
            rs.encode(d, parity);

            for (int j = 0; j < d.length; j++) {
                encoded[interleavingDepth * j + i] = d[j];
            }

            for (int j = 0; j < parity.length; j++) {
                encoded[interleavingDepth * (d.length + j) + i] = parity[j];
            }
        }

        return encoded;
    }
}
