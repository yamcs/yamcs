package org.yamcs.yarch.rocksdb;

/**
 * Iterator that does not support seek. 
 * 
 * @author nm
 *
 */
public interface DbIterator extends AutoCloseable {
    boolean isValid();
    void next();
    void prev();
    byte[] key();
    byte[] value();
    
    @Override 
    public void close();
}
