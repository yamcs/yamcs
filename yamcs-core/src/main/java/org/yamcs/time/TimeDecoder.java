package org.yamcs.time;

import org.yamcs.tctm.PacketPreprocessor;

/**
 * Interface for time decoders used in the {@link PacketPreprocessor}
 * 
 */
public interface TimeDecoder {

    /**
     * Decodes the time from the binary buffer and returns the time in milliseconds. The value returned can be either
     * absolute or relative (this has to be known by the caller)
     * <p>
     * It is assumed that the buffer will contain enough data; if not, an {@link ArrayIndexOutOfBoundsException} will be
     * thrown.
     * 
     * @param buf
     *            - where to read the data from
     * @param offset
     *            - offset in the buffer where the decoding will begin
     * @return decoded time in milliseconds
     * 
     */
    public long decode(byte[] buf, int offset);

    /**
     * Returns the time in an unspecified unit.
     * <p>
     * Can be used when the on-board time is free running.
     * 
     * <p>
     * It is assumed that the buffer will contain enough data; if not, an {@link ArrayIndexOutOfBoundsException} will be
     * thrown.
     * 
     * @param buf
     *            - where to read the data from
     * 
     * @param offset
     *            - offset in the buffer where the decoding will begin
     * @return time
     * 
     */
    public long decodeRaw(byte[] buf, int offset);
}
