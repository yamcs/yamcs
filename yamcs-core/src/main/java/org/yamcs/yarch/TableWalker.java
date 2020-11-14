package org.yamcs.yarch;

import java.util.Set;

import org.yamcs.utils.TimeInterval;

/**
 * Walks over one yarch table providing operations for select, udpdate, delete
 *
 * @author nm
 *
 */
public interface TableWalker {

    default void setPartitionFilter(TimeInterval partitionTimeFilter, Set<Object> partitionValueFilter) {
        throw new UnsupportedOperationException();
    }

    void setPrimaryIndexRange(DbRange tableRange);

    default void setSecondaryIndexRange(DbRange skRange) {
        throw new UnsupportedOperationException();
    }
    
    void walk(TableVisitor visitor);

    void close();
}
