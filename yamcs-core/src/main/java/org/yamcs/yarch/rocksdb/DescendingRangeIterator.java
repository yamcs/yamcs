package org.yamcs.yarch.rocksdb;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.DbRange;

/**
 * Wrapper around a rocksdb iterator that only supports prev() and is restricted to a range.
 * <p>
 * If the rangeStart or rangeEnd are specified, any key which starts or it is equal with them is considered as matching.
 * 
 * @author nm
 *
 */
public class DescendingRangeIterator extends AbstractDbIterator {
    final byte[] rangeStart;
    final byte[] rangeEnd;
    private byte[] curKey;

    /**
     * Creates a new range iterator that iteates in descending order from rangeEnd to rangeStart
     * 
     * @param it
     * @param rangeStart
     * @param rangeEnd
     */
    public DescendingRangeIterator(RocksIterator it, byte[] rangeStart, byte[] rangeEnd) {
        super(it);
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        init();
    }

    public DescendingRangeIterator(RocksIterator it, DbRange range) {
        this(it, range.rangeStart, range.rangeEnd);
    }

    private void init() {
        boolean endFound = false;

        if (rangeEnd == null) {
            iterator.seekToLast();
            if (iterator.isValid()) {
                curKey = iterator.key();
                endFound = true;
            }
        } else {
            byte[] k = ByteArrayUtils.plusOne(rangeEnd);
            iterator.seekForPrev(k);
            if (iterator.isValid()) {
                curKey = iterator.key();
                if (ByteArrayUtils.compare(curKey, rangeEnd) > 0) {
                    iterator.prev();
                    if (iterator.isValid()) {
                        curKey = iterator.key();
                        endFound = true;
                    }
                } else {
                    endFound = true;
                }
            }
        }
        if (endFound) {
            // check that it is not earlier than start
            if (rangeStart != null) {
                int c = ByteArrayUtils.compare(rangeStart, curKey);
                if (c <= 0) {
                    valid = true;
                }
            } else {
                valid = true;
            }
        }
    }

    @Override
    public void prev() {
        checkValid();

        iterator.prev();
        if (iterator.isValid()) {
            curKey = iterator.key();
            if (rangeStart != null) {
                valid = false;
                int c = ByteArrayUtils.compare(rangeStart, curKey);
                if (c <= 0) {
                    valid = true;
                }
            }
        } else {
            valid = false;
        }
    }

    @Override
    public void next() {
        throw new UnsupportedOperationException("this is an desceinding iterator, next() not supported");

    }

    public byte[] key() {
        checkValid();
        return curKey;
    }

    public byte[] value() {
        checkValid();
        return iterator.value();
    }
}
