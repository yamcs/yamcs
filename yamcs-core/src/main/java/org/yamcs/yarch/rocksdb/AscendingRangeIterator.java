package org.yamcs.yarch.rocksdb;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.DbRange;

/**
 * wrapper around a rocksdb iterator that only supports next() and is restricted to a range.
 * 
 * @author nm
 *
 */
public class AscendingRangeIterator implements DbIterator {
    private final RocksIterator iterator;
    final byte[] rangeStart;
    final byte[] rangeEnd;
    private boolean valid = false;
    private byte[] curKey;

    /**
     * Constructs an iterator restricted to a range.
     * <p>
     * The condition for the rangeEnd is such that if rangeEnd is a a prefix for the db key, the key is considered as
     * part of the range.
     * <p>
     * This is also valid for rangeStart but that is normal lexicographic order.
     * 
     */
    public AscendingRangeIterator(RocksIterator it, byte[] rangeStart, byte[] rangeEnd) {
        this.iterator = it;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        init();
    }

    public AscendingRangeIterator(RocksIterator it, DbRange range) {
        this(it, range.rangeStart, range.rangeEnd);
    }

    private void init() {
        boolean startFound = false;
        valid = false;

        if (rangeStart == null) {
            iterator.seekToFirst();
            if (iterator.isValid()) {
                curKey = iterator.key();
                startFound = true;
            }
        } else {
            iterator.seek(rangeStart);

            if (iterator.isValid()) {
                curKey = iterator.key();
                startFound = true;
            }
        }
        if (startFound) {
            // check that it is not beyond the end
            if (rangeEnd != null) {
                int c = ByteArrayUtils.compare(curKey, rangeEnd);
                if (c <= 0) {
                    valid = true;
                }
            } else {
                valid = true;
            }
        }
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void next() {
        if (!valid) {
            throw new IllegalStateException("iterator is not valid");
        }

        iterator.next();
        if (iterator.isValid()) {
            curKey = iterator.key();
            if (rangeEnd != null) {
                int c = ByteArrayUtils.compare(curKey, rangeEnd);
                if (c > 0) {
                    valid = false;
                }
            }
        } else {
            valid = false;
        }
    }

    public byte[] key() {
        if (!valid) {
            throw new IllegalStateException("iterator is not valid");
        }
        return curKey;
    }

    public byte[] value() {
        if (!valid) {
            throw new IllegalStateException("iterator is not valid");
        }
        return iterator.value();
    }

    @Override
    public void close() {
        valid = false;
        iterator.close();
    }

    @Override
    public void prev() {
        throw new UnsupportedOperationException("this is an ascending iterator");
    }
}
