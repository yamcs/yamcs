package org.yamcs.yarch.rocksdb;

import org.rocksdb.RocksIterator;
import org.yamcs.logging.Log;

public abstract class AbstractDbIterator implements DbIterator {
    protected boolean valid = false;
    protected final RocksIterator iterator;
    
    final static Log log = new Log(AbstractDbIterator.class);

    public AbstractDbIterator(RocksIterator it) {
        this.iterator = it;
    }


    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void close() {
        valid = false;
        iterator.close();
    }

    @Override
    public void finalize() {
        if (iterator.isOwningHandle()) {
            log.error("Iterator " + this + " not closed");
        }
    }

    protected void checkValid() {
        if (!valid) {
            throw new IllegalStateException("iterator is not valid");
        }
    }

}
