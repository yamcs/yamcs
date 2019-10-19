package org.yamcs.tctm.ccsds.error;

import org.yamcs.utils.ByteArrayUtils;

public class Ldpc64CltuGenerator extends CltuGenerator {
    final boolean withTail;

    public Ldpc64CltuGenerator(boolean withTail) {
        super(true);
        this.withTail = withTail;
    }

    @Override
    public byte[] makeCltu(byte[] frameData) {
        if (randomize) {
            randomize(frameData);
        }
        int numBlocks = (frameData.length - 1) / 8 + 1;
        int length = 8 + 16 * numBlocks + (withTail ? 32 : 0);

        byte[] encData = new byte[length];
        // start sequence
        ByteArrayUtils.encodeLong(0x0347_76C7_2728_95B0L, encData, 0);

        // data
        int inOffset = 0;
        int outOffset = 8;
        int n = frameData.length / 8;
        for (int i = 0; i < n; i++) {
            System.arraycopy(frameData, inOffset, encData, outOffset, 8);
            Ldpc64Encoder.encode(encData, inOffset, encData, outOffset + 8);
            inOffset += 8;
            outOffset += 16;
        }
        int d = frameData.length - inOffset;
        if (d > 0) {// last block is padded with alternating 0 1 bits
            System.arraycopy(frameData, inOffset, encData, outOffset, d);
            for (int i = 0; i < 8 - d; i++) {
                encData[outOffset + d + i] = 0x55;
            }
            Ldpc64Encoder.encode(encData, outOffset, encData, outOffset + 8);
            outOffset += 16;
        }
        if (withTail) { // tail sequence
            ByteArrayUtils.encodeLong(0x5555_5556_AAAA_AAAAL, encData, outOffset);
            outOffset += 8;
            ByteArrayUtils.encodeLong(0x5555_5555_5555_5555L, encData, outOffset);

        }
        return encData;
    }

    public static class Ldpc64Encoder {

        static final long[] W64 = new long[] {
                0x0E69166BEF4C0BC2L,
                0x7766137EBB248418L,
                0xC480FEB9CD53A713L,
                0x4EAA22FA465EEA11L
        };

        /**
         * Encodes a block of 8 bytes from in:inOffset into a code of 8 bytes stored in out:outOffset
         * 
         * @param in
         * @param inOffset
         * @param out
         * @param outOffset
         */
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
}
