package org.yamcs.tctm;

import org.yamcs.utils.ByteSupplier;

/**
 * Interface for time decoders used in the {@link PacketPreprocessor}
 * 
 */
public interface TimeDecoder {
    /**
     * Decodes the time from the packet and returns the time in milliseconds.
     * The value returned can be either absolute or relative (this has to be known by the caller)
     * 
     * @param byteSupplier
     *            - the bytes will be read from here.
     * @return time in milliseconds
     */
    public long decode(ByteSupplier byteSupplier);

    /**
     * Returns the time in an unspecified unit.
     * <p>
     * Can be used when the on-board time is free running.
     * <p>
     * @param byteSupplier
     *            - the bytes will be read from here.
     * @return time
     */
    public long decodeRaw(ByteSupplier byteSupplier);

    /**
     * Decodes the time from the binary buffer and returns the time in milliseconds.
     * The value returned can be either absolute or relative (this has to be known by the caller)
     * 
     * It is assumed that the buffer will contain enough data; if not, an {@link ArrayIndexOutOfBoundsException} will be
     * thrown.
     * 
     * @param buf
     *            - where to read the data from
     * @param offset
     *            - offset in the buffer where the decoding will begin
     * @return decoded time in milliseconds
     */
    default public long decode(byte[] buf, int offset) {
        return decode(new ByteSupplier() {
            int o = offset;

            @Override
            public byte getAsByte() {
                return buf[o++];
            }
        });
    }

    default public long decodeRaw(byte[] buf, int offset) {
        return decodeRaw(new ByteSupplier() {
            int o = offset;

            @Override
            public byte getAsByte() {
                return buf[o++];
            }
        });
    }
}
