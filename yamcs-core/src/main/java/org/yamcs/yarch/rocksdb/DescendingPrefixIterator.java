package org.yamcs.yarch.rocksdb;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Wrapper around RocksDb iterator that iterates all the keys with a prefix in descending order
 * @author nm
 *
 */
public class DescendingPrefixIterator extends AbstractDbIterator {
    final byte[] prefix;
    byte[] curKey;
    
    public DescendingPrefixIterator(RocksIterator it, byte[] prefix) {
        super(it);
        this.prefix = prefix;
        if(prefix.length==0) {
            throw new IllegalArgumentException("the prefix has to be at least one byte long");
        }
        init();
    }
    
    private void init() {
        try {
            byte[] k = ByteArrayUtils.plusOne(prefix);
            iterator.seek(k);
        } catch (IllegalArgumentException e) {
            //special case the prefix is all 0xFF
            iterator.seekToLast();
        }
        if(!iterator.isValid()) {
            valid = false;
        } else {
            iterator.prev();
            if(!iterator.isValid()) {
                valid = false;
            } else {
                curKey = iterator.key();
                valid = ByteArrayUtils.compare(prefix, curKey) == 0;
            }
        }
    }

    @Override
    public void next() {
        throw new UnsupportedOperationException("next not supported on descending iterator");
    }

    @Override
    public void prev() {
        checkValid();

        iterator.prev();
        if(!iterator.isValid()) {
            valid = false;
        } else {
            curKey = iterator.key();
            valid = ByteArrayUtils.compare(prefix, curKey) == 0;
        }
    }

    @Override
    public byte[] key() {
        checkValid();
        return curKey;
    }

    @Override
    public byte[] value() {
        checkValid();
        return iterator.value();
    }
}
