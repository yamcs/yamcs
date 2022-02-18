package org.yamcs.tctm.ccsds.error;

import org.yamcs.tctm.ccsds.Randomizer;

/**
 * Makes CLTUs from command transfer frames as per
 * CCSDS 231.0-B-3 (TC SYNCHRONIZATION AND CHANNEL CODING)
 * 
 * <p>
 * Implements BCH encoder
 */
public class BchCltuGenerator extends CltuGenerator {
    public static final byte[] CCSDS_START_SEQ = { (byte) 0xEB, (byte) 0x90 };
    public static final byte[] CCSDS_TAIL_SEQ = { (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5,
            (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, 0x79 };

    
    public BchCltuGenerator() {
        this(CCSDS_START_SEQ, CCSDS_TAIL_SEQ);
    }

    public BchCltuGenerator(byte[] startSeq, byte[] tailSeq) {
        super(startSeq, tailSeq);
    }

    @Override
    public byte[] makeCltu(byte[] data, boolean randomize) {
        if (randomize) {
            Randomizer.randomizeTc(data);
        }
        int numBlocks = (data.length - 1) / 7 + 1;
        int length = startSeq.length + 8 * numBlocks + tailSeq.length;

        byte[] encData = new byte[length];
        // start sequence
        System.arraycopy(startSeq, 0, encData, 0, startSeq.length);

        // data
        int inOffset = 0;
        int outOffset = startSeq.length;
        int n = data.length / 7;
        for (int i = 0; i < n; i++) {
            System.arraycopy(data, inOffset, encData, outOffset, 7);
            encData[outOffset + 7] = BchEncoder.encode(encData, outOffset);
            outOffset += 8;
            inOffset += 7;
        }
        int d = data.length - inOffset;
        if (d > 0) {// last block is padded with alternating 0 1 bits
            System.arraycopy(data, inOffset, encData, outOffset, d);
            for (int i = 0; i < 7 - d; i++) {
                encData[outOffset + d + i] = 0x55;
            }
            encData[outOffset + 7] = BchEncoder.encode(encData, outOffset);
            outOffset += 8;
        }
        // tail sequence
        System.arraycopy(tailSeq, 0, encData, outOffset, tailSeq.length);
        return encData;
    }

    public static class BchEncoder {
        static byte r[] = new byte[256];
        static final int POLYNOMIAL = 0x8A;
        static {
            init();
        }

        static void init() {
            int remainder;

            for (int i = 0; i < 256; ++i) {
                remainder = i;
                for (int j = 0; j < 8; j++) {
                    if ((remainder & 0x80) == 0) {
                        remainder = (remainder << 1);
                    } else {
                        remainder = (remainder << 1) ^ POLYNOMIAL;
                    }
                }
                r[i] = (byte) remainder;
            }

        }

        public static byte encode(byte p[]) {
            return encode(p, 0);
        }

        /**
         * Encodes 7 bytes of data from p:offset and returns the result
         * 
         * @param p
         * @param offset
         * @return
         */
        public static byte encode(byte p[], int offset) {
            int remainder = 0;
            int len = 7;

            for (int i = offset; i < offset + len; i++) {
                remainder = r[0xFF & (p[i] ^ remainder)];
            }

            remainder ^= 0xFF;
            remainder &= 0xFE;
            return (byte) remainder;
        }

    }
}
