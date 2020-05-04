package org.yamcs.tctm.ccsds.error;

public class Crc32Calculator {
    final long polynomial;
    int r[] = new int[256];

    public Crc32Calculator(int polynomial) {
        this.polynomial = polynomial;
        init();
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

    public int compute(byte[] data, int offset, int length, int initialValue) {
        int crc = initialValue;

        for (int i = offset; i < offset + length; i++) {
            int idx = (data[i] ^ (crc >> 24)) & 0xff;
            crc = r[idx] ^ (crc << 8);
        }

        return crc;

    }
}
