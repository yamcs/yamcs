package org.yamcs.utils;

import java.util.Arrays;

/**
 * Wrapper around byte array that allows usage as hashmap keys
 * @author nm
 *
 */
public class ByteArrayWrapper {
    private final byte[] data;

    public ByteArrayWrapper(byte[] buf) {
        this.data = buf;
    }
    
    public byte[] getData() {
        return data;
    }
    
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ByteArrayWrapper)) {
            return false;
        }
        return Arrays.equals(data, ((ByteArrayWrapper)other).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }
}
