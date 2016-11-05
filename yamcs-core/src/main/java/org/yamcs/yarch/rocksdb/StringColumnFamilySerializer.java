package org.yamcs.yarch.rocksdb;

import java.nio.charset.StandardCharsets;

public class StringColumnFamilySerializer implements ColumnFamilySerializer {

    @Override
    public byte[] objectToByteArray(Object value) {
        if(value instanceof String) {
            return ((String)value).getBytes(StandardCharsets.US_ASCII);
        }
        throw new RuntimeException("Cannot encode objects of type "+value.getClass());
    }

    @Override
    public Object byteArrayToObject(byte[] b) {
        return new String(b, StandardCharsets.US_ASCII);
    }
}