package org.yamcs.tctm.ccsds.error;

public class Crc32VariantCalculator {
    final long polynomial;

    public Crc32VariantCalculator(int polynomial) {
        this.polynomial = polynomial;
    }

    public long compute(byte[] buffer, int length, int initialValue) {
        long crc = initialValue; // Initial value for CRC-32 calculation

        for (int i = 0; i < length; i++) {
            crc ^= buffer[i] & 0xFF; // XOR with buffer byte
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) == 1) {
                    crc = (crc >>> 1) ^ polynomial; // Right shift and XOR with polynomial
                } else {
                    crc >>>= 1; // Right shift
                }
            }
        }

        return crc ^ 0xFFFFFFFFL; // Final XOR with 0xFFFFFFFF to match the standard CRC-32 output
    }
}
