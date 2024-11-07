package org.yamcs.time;

import org.yamcs.tctm.PacketPreprocessor;

/**
 * Interface for time encoders used in the {@link PacketPreprocessor}
 */
public interface TimeEncoder {

    /**
     * Encodes the Yamcs instant into the binary buffer.
     * <p>
     * It is assumed that the buffer will have enough space; if not, an {@link ArrayIndexOutOfBoundsException} will be
     * thrown.
     * 
     * @param instant
     *            - time in milliseconds to be encoded
     * @param buf
     *            - where to write the encoded time
     * @param offset
     *            - offset in the buffer where the encoding will begin
     * @return the number of bytes encoded
     */
    public int encode(long instant, byte[] buf, int offset);

    /**
     * Encodes the time in an unspecified unit.
     * <p>
     * This can be used when the on-board time is free running.
     * 
     * <p>
     * It is assumed that the buffer will have enough space; if not, an {@link ArrayIndexOutOfBoundsException} will be
     * thrown.
     * 
     * @param time
     *            - time in unspecified units to be encoded
     * @param buf
     *            - where to write the encoded time
     * @param offset
     *            - offset in the buffer where the encoding will begin
     * 
     * @return the number of bytes encoded
     */
    public int encodeRaw(long time, byte[] buf, int offset);

    /**
     * Returns the size in bytes of the encoded time.
     * <p>
     * If the size is variable return -1
     */
    public int getEncodedLength();

}
