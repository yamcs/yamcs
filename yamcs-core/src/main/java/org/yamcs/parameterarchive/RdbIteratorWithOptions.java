package org.yamcs.parameterarchive;

import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;

/**
 * the options object has a snapshot which can be used to get consistent view of the database
 */
public record RdbIteratorWithOptions(RocksIterator it, ReadOptions opts) implements AutoCloseable {
    public RdbIteratorWithOptions {
        if (opts.snapshot() == null) {
            throw new IllegalArgumentException("ReadOptions must have a snapshot set");
        }
    }

    @Override
    public void close() {
        opts.close();
        it.close();
    }
}
