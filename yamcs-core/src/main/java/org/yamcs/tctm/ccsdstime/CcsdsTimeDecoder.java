package org.yamcs.tctm.ccsdstime;

import org.yamcs.utils.ByteSupplier;

/**
 * Decodes CCSDS as per CCSDS 301.0-B-4
 * 
 * The time code is composed by
 * P-Field (preamble field) 8 bits optional
 * T-Field - up to 7 bytes (although it could be longer for custom codes)
 * 
 */
public interface CcsdsTimeDecoder {
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
}
