package org.yamcs.yarch;

import java.util.Iterator;

/**
 * Iterator over histogram records
 * 
 * The iterator offers a partial ordering - for one column the records are sorted by time
 * 
 * @author nm
 *
 */
public interface HistogramIterator extends AutoCloseable, Iterator<HistogramRecord> {
    void seek(byte[] columnValue, long time);
    
    /**
     * Close the iterator 
     */
    void close();
}
