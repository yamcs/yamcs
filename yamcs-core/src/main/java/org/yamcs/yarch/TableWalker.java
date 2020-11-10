package org.yamcs.yarch;

/**
 * Walks over one yarch table providing operations for select, udpdate, delete
 *
 * @author nm
 *
 */
public interface TableWalker extends FilterableTarget {
    void walk(TableVisitor visitor) throws YarchException;
    
    void close() throws YarchException;
    /**
     * Delete all rows matching the filters added with {@link FilterableTarget} method
     */
    void bulkDelete();
}
