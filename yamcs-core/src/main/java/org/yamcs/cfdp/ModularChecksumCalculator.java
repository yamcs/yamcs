package org.yamcs.cfdp;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CFDP modular checksum (CCSDS 727.0-B-5 section 4.2.2).
 * <p>
 * The file is seen as a sequence of 4 bytes big endian unsigned integers aligned at offsets multiple of 4 from the
 * start of the file; the checksum is their sum modulo 2^32. Because of that, the checksum of a file can be computed by
 * adding up the checksums of disjoint segments of the file, in any order, as long as each segment checksum is computed
 * taking into account its offset in the file (see {@link #calculateChecksum(byte[], int, long, int)}).
 */
public class ModularChecksumCalculator {
    private static final VarHandle INT_BE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    public static long calculateChecksum(byte[] data) {
        return calculateChecksum(data, 0, 0, data.length);
    }

    /**
     * Calculate checksum for a data segment of given length starting at the given offset inside the file (not in the
     * data buffer passed as parameter!)
     * <p>
     * The data is taken from data[0] to data[length-1].
     */
    static long calculateChecksum(byte[] data, long fileOffset, long length) {
        return calculateChecksum(data, 0, fileOffset, (int) length);
    }

    /**
     * Calculate checksum for a data segment of given length starting at the given offset inside the file.
     * <p>
     * The data is taken from data[dataOffset] to data[dataOffset+length-1].
     * <p>
     * The beginning is padded with 0 according to the file offset and the end is padded with 0 to reach a multiple of
     * 4 bytes.
     * <p>
     * Adding up the checksum for the file segments matches the checksum of the file, no matter the order and the size
     * of the segments, as long as the segments do not overlap.
     */
    public static long calculateChecksum(byte[] data, int dataOffset, long fileOffset, int length) {
        long sum = 0;
        int i = dataOffset;
        int end = dataOffset + length;
        int k = (int) (fileOffset & 3);
        if (k != 0 && i < end) {
            long x = 0;
            while (k < 4 && i < end) {
                x |= (long) (data[i++] & 0xFF) << ((3 - k) << 3);
                k++;
            }
            sum += x;
        }
        int end4 = end - 3;
        while (i < end4) {
            sum += ((int) INT_BE.get(data, i)) & 0xFFFFFFFFL;
            i += 4;
        }
        if (i < end) {
            long x = 0;
            int shift = 24;
            while (i < end) {
                x |= (long) (data[i++] & 0xFF) << shift;
                shift -= 8;
            }
            sum += x;
        }
        return sum & 0xFFFFFFFFL;
    }

    /**
     * Calculate the checksum of the remaining bytes in the buffer, considering that the byte at the buffer position is
     * located at fileOffset in the file. The buffer position is advanced to the limit.
     */
    public static long calculateChecksum(ByteBuffer buf, long fileOffset) {
        long sum = 0;
        int k = (int) (fileOffset & 3);
        if (k != 0 && buf.hasRemaining()) {
            long x = 0;
            while (k < 4 && buf.hasRemaining()) {
                x |= (long) (buf.get() & 0xFF) << ((3 - k) << 3);
                k++;
            }
            sum += x;
        }
        ByteOrder order = buf.order();
        buf.order(ByteOrder.BIG_ENDIAN);
        // unroll a bit to give the JIT something to work with
        while (buf.remaining() >= 16) {
            sum += (buf.getInt() & 0xFFFFFFFFL) + (buf.getInt() & 0xFFFFFFFFL)
                    + (buf.getInt() & 0xFFFFFFFFL) + (buf.getInt() & 0xFFFFFFFFL);
        }
        while (buf.remaining() >= 4) {
            sum += buf.getInt() & 0xFFFFFFFFL;
        }
        buf.order(order);
        if (buf.hasRemaining()) {
            long x = 0;
            int shift = 24;
            while (buf.hasRemaining()) {
                x |= (long) (buf.get() & 0xFF) << shift;
                shift -= 8;
            }
            sum += x;
        }
        return sum & 0xFFFFFFFFL;
    }
}
