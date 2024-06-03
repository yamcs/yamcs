package org.yamcs.time;

import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Decodes time on a fixed number of bytes.
 * 
 * <p>
 * Parameters are the size in bytes of the encoded time as well as a multiplier.
 * <p>
 * The multiplier is used to convert the extracted value to milliseconds in the {@link #decode(byte[], int)}} method.
 * The multiplier is not used in the {@link #decodeRaw(byte[], int)} method.
 * 
 * 
 * @author nm
 *
 */
public class FixedSizeTimeDecoder implements TimeDecoder {
    final ByteOrder byteOrder;
    final double multiplier;
    final int size;

    /**
     * @param byteOrder
     *            byte order of the encoded integer value
     * @param size
     *            how many bytes to be used to decode the time. Has to be maximum 8, otherwise a
     *            IllegalArgumentException will be thrown
     * @param multiplier
     *            the multiplier to convert the extracted value to milliseconds.
     *
     */
    public FixedSizeTimeDecoder(ByteOrder byteOrder, int size, double multiplier) {
        this.byteOrder = byteOrder;
        this.multiplier = multiplier;
        if (size != 4 && size != 8) {
            throw new IllegalArgumentException("Invalid size " + size + " (should be between 4 or 8)");
        }
        this.size = size;

    }

    private long get(byte[] buf, int offset) {
        if (offset + size > buf.length) {
            throw new BufferUnderflowException();
        }
        switch (size) {
        case 4:
            return (byteOrder == ByteOrder.BIG_ENDIAN)
                    ? ByteArrayUtils.decodeInt(buf, offset)
                    : ByteArrayUtils.decodeIntLE(buf, offset);
        case 8:
            return (byteOrder == ByteOrder.BIG_ENDIAN)
                    ? ByteArrayUtils.decodeLong(buf, offset)
                    : ByteArrayUtils.decodeLongLE(buf, offset);
        default:
            throw new IllegalStateException("unknown size " + size);
        }
    }

    @Override
    public long decode(byte[] buf, int offset) {
        return (long) (get(buf, offset) * multiplier);
    }

    @Override
    public long decodeRaw(byte[] buf, int offset) {
        return get(buf, offset);
    }

}
