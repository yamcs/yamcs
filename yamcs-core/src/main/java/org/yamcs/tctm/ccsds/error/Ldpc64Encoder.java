package org.yamcs.tctm.ccsds.error;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Encoder for the LDPC codec (n=128,k=64) as specified in
 * RECOMMENDED STANDARD FOR TC SYNCHRONIZATION AND CHANNEL CODING
 * CCSDS 231.0-B-3 September 2017
 * 
 * @author nm
 *
 */
public class Ldpc64Encoder {

    static final long[] W64 = new long[] {
            0x0E69166BEF4C0BC2L,
            0x7766137EBB248418L,
            0xC480FEB9CD53A713L,
            0x4EAA22FA465EEA11L
    };

    public static void encode(byte[] in, int inOffset, byte[] out, int outOffset) {
        long r = 0;
        for (int i = 0; i < 4; i++) {
            long wl = W64[i];
            for (int j = 0; j < 2; j++) {
                int d = in[inOffset++];
                for (int k = 7; k >= 0; k--) {
                    if (((d >>> k) & 1) == 1) {
                        r = r ^ wl;
                    }
                    wl = rotrGroupOf16(wl);
                }
            }
        }
        ByteArrayUtils.encodeLong(r, out, outOffset);
    }


    // Circularly shift to the right each group of 16 bits
    static long rotrGroupOf16(long x) {
        return (x >>> 1 & 0x7FFF7FFF7FFF7FFFL) | ((x & 0x0001000100010001L) << 15);
    }
}
