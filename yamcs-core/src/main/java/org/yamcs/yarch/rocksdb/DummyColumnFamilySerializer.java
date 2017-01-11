package org.yamcs.yarch.rocksdb;

/**
 * use this as a default column family serializer in case we don't have a better one (e.g. when performing backups)
 *
 */
public class DummyColumnFamilySerializer implements ColumnFamilySerializer {
    @Override
    public byte[] objectToByteArray(Object value) {
        return (byte[]) value;
    }

    @Override
    public Object byteArrayToObject(byte[] b) {
        return b;
    }

}