package org.yamcs.tctm.ccsds;

/**
 * 
 * Encoder for the BCH as specified in
 * RECOMMENDED STANDARD FOR TC SYNCHRONIZATION AND CHANNEL CODING
 * CCSDS 231.0-B-3 September 2017
 * 
 * @author nm
 *
 */
public class BchEncoder {
    static byte r[] = new byte[256];
    static final int POLYNOMIAL = 0x8A;
    static {
        init();
    }

    static void init() {
        int remainder;

        for (int i = 0; i < 256; ++i)  {
            remainder = i;
            for (int j = 0; j<8; j++) {
                if ((remainder & 0x80) ==0) {
                    remainder = (remainder << 1);
                } else {
                    remainder = (remainder << 1) ^ POLYNOMIAL;
                }
            }
            r[i] = (byte)remainder;
        }

    }

    public static byte encode(byte p[], int len)  {
        int remainder = 0;

        for (int i = 0; i < len; i++) {
            remainder = r[0xFF & (p[i] ^ remainder)];
        }

        remainder ^= 0xFF;
        remainder &= 0xFE;
        return (byte) remainder;
    }
  
}

