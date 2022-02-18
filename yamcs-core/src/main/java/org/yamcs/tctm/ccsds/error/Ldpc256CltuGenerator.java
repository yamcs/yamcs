package org.yamcs.tctm.ccsds.error;

import org.yamcs.tctm.ccsds.Randomizer;
import org.yamcs.utils.ByteArrayUtils;

public class Ldpc256CltuGenerator extends CltuGenerator {
    static final public byte[] CCSDS_START_SEQ = new byte[] { 0x03, 0x47, 0x76, (byte) 0xC7, 0x27, 0x28, (byte) 0x95,
            (byte) 0xB0 };

    public Ldpc256CltuGenerator() {
        this(CCSDS_START_SEQ, EMPTY_SEQ);
    }

    public Ldpc256CltuGenerator(byte[] startSeq, byte[] tailSeq) {
        super(startSeq, tailSeq);
    }

    @Override
    public byte[] makeCltu(byte[] frameData, boolean randomize) {
        if (!randomize) {
            throw new IllegalArgumentException("Randomization is mandatory for the LDPC codec");
        }
        int numBlocks = (frameData.length - 1) / 32 + 1;
        int length = startSeq.length + 64 * numBlocks + tailSeq.length;

        byte[] encData = new byte[length];
        // start sequence
        System.arraycopy(startSeq, 0, encData, 0, startSeq.length);

        // data
        int inOffset = 0;
        int outOffset = startSeq.length;
        int n = frameData.length / 32;
        for (int i = 0; i < n; i++) {
            System.arraycopy(frameData, inOffset, encData, outOffset, 32);
            Ldpc256Encoder.encode(encData, outOffset, encData, outOffset + 32);
            Randomizer.randomizeTc(encData, outOffset, 64);
            inOffset += 32;
            outOffset += 64;
        }
        int d = frameData.length - inOffset;
        if (d > 0) {// last block is padded with alternating 0 1 bits
            System.arraycopy(frameData, inOffset, encData, outOffset, d);
            for (int i = 0; i < 32 - d; i++) {
                encData[outOffset + d + i] = 0x55;
            }
            Ldpc256Encoder.encode(encData, outOffset, encData, outOffset + 32);
            Randomizer.randomizeTc(encData, outOffset, 64);
            outOffset += 64;
        }
        if (tailSeq.length > 0) { // tail sequence
            System.arraycopy(tailSeq, 0, encData, outOffset, tailSeq.length);
        }

        return encData;
    }

    public static class Ldpc256Encoder {

        static final long[][] W256 = new long[][] {
                { 0x1D21794A22761FAEL, 0x59945014257E130DL, 0x74D6054003794014L, 0x2DADEB9CA25EF12EL },
                { 0x60E0B6623C5CE512L, 0x4D2C81ECC7F469ABL, 0x20678DBFB7523ECEL, 0x2B54B906A9DBE98CL },
                { 0xF6739BCF54273E77L, 0x167BDA120C6C4774L, 0x4C071EFF5E32A759L, 0x3138670C095C39B5L },
                { 0x28706BD045300258L, 0x2DAB85F05B9201D0L, 0x8DFDEE2D9D84CA88L, 0xB371FAE63A4EB07EL }
        };

        /**
         * Encodes a block of 32 bytes from in:inOffset into a code of 32 bytes stored in out:outOffset
         * 
         * @param in
         * @param inOffset
         * @param out
         * @param outOffset
         */
        public static void encode(byte[] in, int inOffset, byte[] out, int outOffset) {
            long r0 = 0;
            long r1 = 0;
            long r2 = 0;
            long r3 = 0;

            for (int i = 0; i < 4; i++) {
                long wl0 = W256[i][0];
                long wl1 = W256[i][1];
                long wl2 = W256[i][2];
                long wl3 = W256[i][3];

                for (int j = 0; j < 8; j++) {
                    int d = in[inOffset++];
                    for (int k = 7; k >= 0; k--) {
                        if (((d >>> k) & 1) == 1) {
                            r0 = r0 ^ wl0;
                            r1 = r1 ^ wl1;
                            r2 = r2 ^ wl2;
                            r3 = r3 ^ wl3;
                        }
                        wl0 = Long.rotateRight(wl0, 1);
                        wl1 = Long.rotateRight(wl1, 1);
                        wl2 = Long.rotateRight(wl2, 1);
                        wl3 = Long.rotateRight(wl3, 1);
                    }
                }
            }
            ByteArrayUtils.encodeLong(r0, out, outOffset);
            ByteArrayUtils.encodeLong(r1, out, outOffset + 8);
            ByteArrayUtils.encodeLong(r2, out, outOffset + 16);
            ByteArrayUtils.encodeLong(r3, out, outOffset + 24);
        }

        // this performs slightly worse than the method above
        public static void encode2(byte[] in, int inOffset, byte[] out, int outOffset) {
            long[] r = new long[4];// {0, 0, 0, 0};

            for (int i = 0; i < 4; i++) {
                long[] wl = { W256[i][0], W256[i][1], W256[i][2], W256[i][3] };// Arrays.copyOf(W256[i], 4);

                for (int j = 0; j < 8; j++) {
                    int d = in[inOffset++];
                    for (int k = 7; k >= 0; k--) {
                        if (((d >>> k) & 1) == 1) {
                            for (int v = 0; v < 4; v++) {
                                r[v] = r[v] ^ wl[v];
                            }
                        }
                        for (int v = 0; v < 4; v++) {
                            wl[v] = Long.rotateRight(wl[v], 1);
                        }
                    }
                }
            }
            for (int v = 0; v < 4; v++) {
                ByteArrayUtils.encodeLong(r[v], out, outOffset + 8 * v);
            }
        }
    }

}
