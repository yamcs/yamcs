package org.yamcs.yarch.rocksdb2;


/**
 * In RocksDB Column Families are identified by byte[]
 * 
 * This class allows to use other objects for column families by providing a serialization/deserialization from Object to byte[]
 * 	
 */
public interface ColumnFamilySerializer {
    public byte[] objectToByteArray(Object value);
    public Object byteArrayToObject(byte[] b);	
}
