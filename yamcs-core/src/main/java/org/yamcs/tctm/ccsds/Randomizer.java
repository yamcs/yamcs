package org.yamcs.tctm.ccsds;

/**
 * CCSDS randomizer as per CCSDS 131.0-B-3(TM) and CCSDS 231.0-B-3 (TC)
 */
public class Randomizer {
    static byte[] tmseq = new byte[255];
    static byte[] tcseq = new byte[255];
    static {
        int lfsr = 0xFF;
        int bit;

        for (int i = 0; i < 255; i++) {
            tmseq[i] = 0;
            for (int j = 0; j < 8; j++) {
                tmseq[i] = (byte) ((tmseq[i] << 1) | (lfsr & 1));
                bit = ((lfsr >> 0) ^ (lfsr >> 3) ^ (lfsr >> 5) ^ (lfsr >> 7)) & 1;
                lfsr = (lfsr >> 1) | (bit << 7);
            }
        }

        lfsr = 0xFF;

        for (int i = 0; i < 255; i++) {
            tcseq[i] = 0;
            for (int j = 0; j < 8; j++) {
                tcseq[i] = (byte) ((tcseq[i] << 1) | (lfsr & 1));
                bit = ((lfsr >> 0) ^ (lfsr >> 1) ^ (lfsr >> 2) ^ (lfsr >> 3) ^ (lfsr >> 4) ^ (lfsr >> 6)) & 1;
                lfsr = (lfsr >> 1) | (bit << 7);
            }
        }
    }

    static void xor(byte[] buf, int offset, int length, byte[] seq) {
        int j = 0;

        for (int i = offset; i < offset + length; i++) {
            buf[i] = (byte) (buf[i] ^ seq[j]);
            j++;
            if (j == 255)
                j = 0;
        }
    }

    /**
     * Randomize the buffer according to CCSDS 131.0-B-3 pseudo-randomizer
     * 
     * @param buf
     */
    public static void randomizeTm(byte[] buf) {
        xor(buf, 0, buf.length, tmseq);
    }

    public static void randomizeTm(byte[] buf, int offset, int length) {
        xor(buf, offset, length, tmseq);
    }

    /**
     * Randomize the buffer according to CCSDS 231.0-B-3 pseudo-randomizer
     * 
     */
    public static void randomizeTc(byte[] buf) {
        xor(buf, 0, buf.length, tcseq);
    }

    /**
     * Randomize the buffer starting at offset for length bytes according to CCSDS 231.0-B-3 pseudo-randomizer
     * 
     */
    public static void randomizeTc(byte[] buf, int offset, int length) {
        xor(buf, offset, length, tcseq);
    }
}
