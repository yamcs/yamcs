package org.yamcs.yarch.rocksdb;

import java.util.Arrays;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;


/**
 * wrapper around a rocksdb iterator that only supports prev() and is restricted to a range.
 * 
 * if strictStart=true 
 *   then the first rangeStart.length bytes of the key are compared with the rangeStart, if they are equal the record is skipped.
 *    same is valid for strictEnd  
 *  
 *  So with the following DB:
 *  1: 0x00 0x00 0x00 0x02
 *  2: 0x01 0x01 0x01 0x00
 *  3: 0x01 0x01 0x01 0x03
 *  4: 0x01 0x01 0x02 0x00
 *  
 *  and rangeStart = 0x01 0x01 0x01, strictStart=true
 *  
 *  the iterator will skip records 1,2 and 3 and start from record 4
 *  
 * 
 * @author nm
 *
 */
public class DescendingRangeIterator implements DbIterator {
    final RocksIterator iterator;
    final byte[] rangeStart;
    final boolean strictStart;
    final byte[] rangeEnd;
    final boolean strictEnd;
    boolean valid = false;
    private byte[] curKey;

    /**
     * Creates a new range iterator that iteates in descending order from rangeEnd to rangeStart
     * @param it
     * @param rangeStart
     * @param strictStart
     * @param rangeEnd
     * @param strictEnd
     */
    public DescendingRangeIterator(RocksIterator it, byte[] rangeStart, boolean strictStart, byte[] rangeEnd, boolean strictEnd) {
        this.iterator = it;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.strictStart = strictStart;
        this.strictEnd = strictEnd;       
        init();
    }

    private void init() {
        boolean endFound = false;
        
        if(rangeEnd == null) {
            iterator.seekToLast();
            if(iterator.isValid()) {
                curKey = iterator.key();
                endFound = true;
            }
        } else {
            iterator.seek(rangeEnd);    //seek moves cursor beyond the match

            if(iterator.isValid()) {
                curKey = iterator.key();
                if(!strictEnd  && Arrays.equals(curKey, rangeEnd)) {
                    endFound = true;
                } else {
                    iterator.prev();
                    if(iterator.isValid()) {
                        curKey = iterator.key();
                        endFound = true;
                    } 
                }
            } else { //reached the end of the table -> check the last entry
                iterator.seekToLast();
                if(iterator.isValid()) {
                    curKey = iterator.key();
                    endFound = true;
                }
            }
        }
        if(endFound) {
            //check that it is not earlier than start
            if(rangeStart!=null) {
                int c = ByteArrayUtils.compare(rangeStart, curKey);
                if((strictStart && c<0) || (!strictStart && c<=0)) {
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
        if(!valid) {
            throw new IllegalStateException("iterator is not valid");
        }

        iterator.prev();
        if(iterator.isValid()) {
            curKey = iterator.key();
            if(rangeStart!=null) {
                valid = false;
                int c = ByteArrayUtils.compare(rangeStart, curKey);
                if((strictStart && c<0) || (c<=0)) {
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
        if(!valid) throw new IllegalStateException("iterator is not valid");
        return curKey;
    }

    public byte[] value() {
        if(!valid) throw new IllegalStateException("iterator is not valid");
        return iterator.value();
    }


    @Override
    public void close() {
        valid = false;
        iterator.close();
    }
}
