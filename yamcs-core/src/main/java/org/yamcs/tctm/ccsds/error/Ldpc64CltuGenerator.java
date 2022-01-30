package org.yamcs.tctm.ccsds.error;

import org.yamcs.tctm.ccsds.Randomizer;
import org.yamcs.utils.ByteArrayUtils;

public class Ldpc64CltuGenerator extends CltuGenerator {
    static public final byte[] CCSDS_START_SEQ = { 0x03, 0x47, 0x76, (byte) 0xC7, 0x27, 0x28, (byte) 0x95,
            (byte) 0xB0 };
    static public final byte[] CCSDS_TAIL_SEQ = { 0x55, 0x55, 0x55, 0x56, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
            (byte) 0xAA,
            0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55 };

    public Ldpc64CltuGenerator(byte[] startSeq, byte[] tailSeq) {
        super(startSeq, tailSeq);
    }

    public Ldpc64CltuGenerator(boolean withTail) {
        this(CCSDS_START_SEQ, withTail ? CCSDS_TAIL_SEQ : EMPTY_SEQ);
    }

    @Override
    public byte[] makeCltu(byte[] frameData, boolean randomize) {
        if (!randomize) {
            throw new IllegalArgumentException("Randomization is mandatory for the LDPC codec");
        }
        int numBlocks = (frameData.length - 1) / 8 + 1;
        int length = startSeq.length + 16 * numBlocks + tailSeq.length;

        byte[] encData = new byte[length];
        // start sequence
        System.arraycopy(startSeq, 0, encData, 0, startSeq.length);

        // data
        int inOffset = 0;
        int outOffset = startSeq.length;
        int n = frameData.length / 8;
        for (int i = 0; i < n; i++) {
            System.arraycopy(frameData, inOffset, encData, outOffset, 8);
            Ldpc64Encoder.encode(encData, outOffset, encData, outOffset + 8);
            Randomizer.randomizeTc(encData, outOffset, 16);
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
            Randomizer.randomizeTc(encData, outOffset, 16);
            outOffset += 16;
        }
        if (tailSeq.length > 0) { // tail sequence
            System.arraycopy(tailSeq, 0, encData, outOffset, tailSeq.length);
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
