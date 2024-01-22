package org.yamcs.time;

import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Decodes milliseconds from fractional seconds stored in a 64-bit float
 */
public class Float64TimeDecoder implements TimeDecoder {

    private final ByteOrder byteOrder;

    public Float64TimeDecoder(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    private double get(byte[] buf, int offset) {
        if (offset + 8 > buf.length) {
            throw new BufferUnderflowException();
        }

        var longBits = (byteOrder == ByteOrder.LITTLE_ENDIAN)
                ? ByteArrayUtils.decodeLongLE(buf, offset)
                : ByteArrayUtils.decodeLong(buf, offset);
        return Double.longBitsToDouble(longBits);
    }

    @Override
    public long decode(byte[] buf, int offset) {
        // Return the value in milliseconds
        return (long) (get(buf, offset) * 1000);
    }

    @Override
    public long decodeRaw(byte[] buf, int offset) {
        // Same as normal decode
        return decode(buf, offset);
    }
}
