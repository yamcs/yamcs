package org.yamcs.cfdp;

public class ChecksumCalculator {
    public static long calculateChecksum(byte[] data) {
        return calculateChecksum(data, 0, data.length);
    }

    /**
     * Calculate checksum for a data segment of given length starting at the given offset inside the file (not in the
     * data buffer passed as parameter!)
     * <p>
     * length has to be smaller than data.lenght but offset can be arbitrary. It is used to pad the beginning with 0 to
     * reach multiple of 4 bytes.
     * <p>
     * The end (after length) is also padded with 0 to reach multiple of 4 bytes.
     * <p>
     * Adding up the checksum for the file segments should match the checksum of the file, no matter the order and the
     * size of the segments.
     * 
     */
    static long calculateChecksum(byte[] data, long fileOffset, long length) {
        int k = (int) (fileOffset & 3);
        long checksum = 0;
        int i = 0;
        long x = 0;
        while (i < length) {
            x = (x << 8) + (data[i] & 0xFF);
            i++;
            k++;
            if (k == 4) {
                checksum += x;
                x = 0;
                k = 0;
            }
        }

        x = x << ((4 - k)<<3);
        checksum += x;

        return checksum & 0xFFFFFFFFl;
    }
}
