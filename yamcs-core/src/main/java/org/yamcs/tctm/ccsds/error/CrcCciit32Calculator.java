package org.yamcs.tctm.ccsds.error;

import java.nio.ByteBuffer;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.ErrorDetectionWordCalculator;

/**
 * Cylcic Redundancy Check (CRC-CCIIT 0xFFFF) with the polynomial:
 * <p>
 * 1 + x^5 + x^12 + x^16
 * <p>
 * Also specified in: CCSDS TC Space Data Link Protocol (CCSDS 232.0-B-3), CCSDS TM Space Data Link Protocol (CCSDS
 * 132.0-B-3) and CCSDS AOS Space Data Link Protocol (CCSDS 732.0-B-4)
 *
 */
public class CrcCciit32Calculator implements ErrorDetectionWordCalculator {
    final int initialValue;
    final int polynomial = 0x1EDC6F41; // 0001 0000 0010 0001 (0, 5, 12)
    Crc32Calculator cc;

    public CrcCciit32Calculator() {
        initialValue = 0xFFFFFFFF;
        cc = new Crc32Calculator(polynomial);
    }

    public CrcCciit32Calculator(YConfiguration c) {
        initialValue = c.getInt("initialValue", 0xFFFFFFFF);
        boolean xor = c.getBoolean("useXor", false);

        cc = new Crc32Calculator(polynomial, xor);
    }

    @Override
    public int compute(byte[] data, int offset, int length) {
        return cc.compute2(data, offset, length, initialValue);
    }

    public int compute(ByteBuffer data, int offset, int length) {
        return cc.compute(data, offset, length, initialValue);
    }

    @Override
    public int sizeInBits() {
        return 32;
    }
}
