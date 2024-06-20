package org.yamcs.tctm.ccsds.error;

import java.nio.ByteBuffer;

public class Crc32Calculator {
    final long polynomial;
    int r[] = new int[256];

    boolean xor = false;

    public Crc32Calculator(int polynomial) {
        this.polynomial = polynomial;
        init2();
    }

    public Crc32Calculator(int polynomial, boolean xor) {
        this.polynomial = polynomial;
        this.xor = xor;

        init2();
    }

    void init2() {
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int j = 8; j > 0; j--) {
                if ((crc & 1) == 1) {
                    crc = (crc >>> 1) ^ (int) polynomial;
                } else {
                    crc = crc >>> 1;
                }
            }
            r[i] = crc;
        }
    }



    void init() {
        long remainder;
        for (int dividend = 0; dividend < 256; dividend++) {
            remainder = dividend << 24;

            for (int j = 0; j < 8; j++) {
                if ((remainder & 0x80000000L) == 0) {
                    remainder = (remainder << 1);
                } else {
                    remainder = (remainder << 1) ^ polynomial;
                }
                
            }
            
            r[dividend] = (int) remainder;
        }
    }

    public int compute2(byte[] data, int offset, int length, int initialValue) {
        int crc = initialValue;
        for (byte b : data) {
            crc = (crc >>> 8) ^ r[(crc ^ b) & 0xFF];
        }
        return crc ^ 0xFFFFFFFF;  // Final XOR value
    }


    public int compute(byte[] data, int offset, int length, int initialValue) {
        int crc = initialValue;

        for (int i = offset; i < offset + length; i++) {
            int idx = (data[i] ^ (crc >> 24)) & 0xff;
            crc = r[idx] ^ (crc << 8);
        }

        if (xor)
            return crc ^ 0xFFFF;

        return crc;
    }

    public int compute(ByteBuffer bb, int offset, int length, int initialValue) {
        int crc = initialValue;

        for (int i = offset; i < offset + length; i++) {
            int idx = (bb.get(i) ^ (crc >> 24)) & 0xff;
            crc = r[idx] ^ (crc << 8);
        }

        if (xor)
            return crc ^ 0xFFFF;

        return crc;
    }
}
