package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ArchiveRecord;

/**
 * Used by {@link IndexRequestProcessor}
 */
public interface IndexRequestListener {
    enum IndexType {HISTOGRAM, COMPLETENESS};
    /**
     * Called at the beginning or when the table/type changes in case multiple indices are sent.
     * If only one type of index is requested, it can be ignored
     */
    default void begin(IndexType type, String tblName) {};
    
    /**
     * Called with new data
     * @param ar
     */
    void processData( ArchiveRecord ar);
    
    /**
     * Called right after the processing ended, either successfully or through
     * error.
     * 
     * If a paged request has been performed, the token can be used to retrieve the next chunk
     */
    void finished(String token, boolean success);
}
