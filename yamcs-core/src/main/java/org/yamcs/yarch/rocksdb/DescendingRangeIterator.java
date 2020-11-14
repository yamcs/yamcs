package org.yamcs.yarch.rocksdb;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.DbRange;

/**
 * Wrapper around a rocksdb iterator that only supports prev() and is restricted to a range.
 * <p>
 * If the rangeStart or rangeEnd are specified, any key which starts or it's equal with them is considered as matching.
 * <p>
 * That means they will all be returned when {@code strictStart/End=false}
 * or they will all be skipped if {@code strictStart/End=true}.
 * 
 * <p>
 * For example, with the following DB:
 * 
 * <pre>
 *  1: 0x00 0x00 0x00 0x02
 *  2: 0x01 0x01 0x01 0x00
 *  3: 0x01 0x01 0x01 0x03
 *  4: 0x01 0x01 0x02 0x00
 * </pre>
 * 
 * and {@code rangeStart = 0x01 0x01 0x01, strictStart=true}
 * <p>
 * the iterator will only return record 4
 * <p>
 * with {@code rangeEnd = 0x01, strictEnd = false}, the iterator will return all records in descending order 4,3,2,1.
 * 
 * 
 * @author nm
 *
 */
public class DescendingRangeIterator implements DbIterator {
    final RocksIterator iterator;
    final byte[] rangeStart;
    final byte[] rangeEnd;
    boolean valid = false;
    private byte[] curKey;

    /**
     * Creates a new range iterator that iteates in descending order from rangeEnd to rangeStart
     * 
     * @param it
     * @param rangeStart
     * @param rangeEnd
     */
    public DescendingRangeIterator(RocksIterator it, byte[] rangeStart, byte[] rangeEnd) {
        this.iterator = it;
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
            iterator.seek(rangeEnd); // seek moves cursor beyond the match but we have to move it
                                     // beyond any key which starts with rangeEnd
            while (iterator.isValid()) {
                curKey = iterator.key();

                if (ByteArrayUtils.compare(curKey, rangeEnd) == 0) {
                    iterator.next();
                } else {
                    break;
                }
            }
            if (iterator.isValid()) {
                iterator.prev();
                if (iterator.isValid()) {
                    curKey = iterator.key();
                    endFound = true;
                }
            } else { // reached the end of the table -> check the last entry
                iterator.seekToLast();
                if (iterator.isValid()) {
                    curKey = iterator.key();
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
    public boolean isValid() {
        return valid;
    }

    @Override
    public void prev() {
        if (!valid) {
            throw new IllegalStateException("iterator is not valid");
        }

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
        if (!valid)
            throw new IllegalStateException("iterator is not valid");
        return curKey;
    }

    public byte[] value() {
        if (!valid)
            throw new IllegalStateException("iterator is not valid");
        return iterator.value();
    }

    @Override
    public void close() {
        valid = false;
        iterator.close();
    }
}
