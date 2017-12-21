package org.yamcs.yarch.oldrocksdb;

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
