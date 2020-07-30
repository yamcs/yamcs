package org.yamcs.tctm;

/**
 * Computes the checksum inside the CCSDS packet
 * @author nm
 *
 */
public interface ErrorDetectionWordCalculator {
    /**
     * Compute the checksum on the data buffer starting at offset and taking into account length bytes
     * @param data
     * @param offset
     * @param length
     * @return
     */
    public int compute(byte[] data, int offset, int length);
    /**
     * 
     * @return size in bits of the calculated checksum (max 32)
     */
    public int sizeInBits();
}
