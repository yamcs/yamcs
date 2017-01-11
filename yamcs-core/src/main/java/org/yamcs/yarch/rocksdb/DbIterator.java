package org.yamcs.yarch.rocksdb;

/**
 * Iterator that does not support seek. 
 * 
 * @author nm
 *
 */
public interface DbIterator {
    boolean isValid();
    void next();
    void prev();
    void close();
    byte[] key();
    byte[] value();
}
