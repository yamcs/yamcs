package org.yamcs.parameterarchive;

import org.yamcs.utils.PeekingIterator;

/**
 * RocksDb style iterator. The advantage over the standard java iterator is that the value can be looked at and thus
 * used in priority queues to run multiple of them in parallel.
 */
public interface ParchiveIterator<T> extends PeekingIterator<T>, AutoCloseable {
    public void close();
}
