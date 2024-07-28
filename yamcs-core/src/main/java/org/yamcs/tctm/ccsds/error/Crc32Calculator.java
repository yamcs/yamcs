package org.yamcs.tctm.ccsds.error;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

public class Crc32Calculator {
    final long polynomial;
    int r[] = new int[256];

    boolean xor = false;

    public Crc32Calculator(int polynomial) {
        this.polynomial = polynomial;
        init();
    }

    public Crc32Calculator(int polynomial, boolean xor) {
        this.polynomial = polynomial;
        this.xor = xor;

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

    public int computeCrc32c(byte[] data, int offset, int length) {

        CRC32C crc32c = new CRC32C();
        crc32c.update(data, offset, length);

        return (int) crc32c.getValue();
    }

    public int computeCrc32c(ByteBuffer bb, int offset, int length) {
        byte[] data = bb.array();

        CRC32C crc32c = new CRC32C();
        crc32c.update(data, offset, length);

        return (int) crc32c.getValue();
    }
}
