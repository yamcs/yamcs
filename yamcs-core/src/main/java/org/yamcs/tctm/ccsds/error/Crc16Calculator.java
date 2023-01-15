package org.yamcs.tctm.ccsds.error;

import java.nio.ByteBuffer;

public class Crc16Calculator {
    final int polynomial;
    short r[] = new short[256];

    public Crc16Calculator(int polynomial) {
        this.polynomial = polynomial;
        init();
    }

    void init() {
        int remainder;
        for (int dividend = 0; dividend < 256; dividend++) {
            remainder = dividend << 8;

            for (int j = 0; j < 8; j++) {
                if ((remainder & 0x8000) == 0) {
                    remainder = (remainder << 1);
                } else {
                    remainder = (remainder << 1) ^ polynomial;
                }
            }

            r[dividend] = (short) remainder;
        }
    }

    public int compute(byte[] data, int offset, int length, int initialValue) {
        int crc = initialValue;

        for (int i = offset; i < offset + length; i++) {
            int idx = (data[i] ^ (crc >> 8)) & 0xff;
            crc = r[idx] ^ (crc << 8);
        }

        return crc & 0xFFFF;

    }

    public int compute(ByteBuffer bb, int offset, int length, int initialValue) {
        int crc = initialValue;

        for (int i = offset; i < offset + length; i++) {
            int idx = (bb.get(i) ^ (crc >> 8)) & 0xff;
            crc = r[idx] ^ (crc << 8);
        }

        return crc & 0xFFFF;
    }
}
