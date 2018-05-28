package org.yamcs.yarch.rocksdb;


import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;

/**
 * wrapper around a rocksdb iterator that only supports next() and is restricted to a range.
 * 
 * 
 * 
 * @author nm
 *
 */
public class AscendingRangeIterator implements DbIterator {
    private final RocksIterator iterator;
    final byte[] rangeStart;
    final boolean strictStart;
    final byte[] rangeEnd;
    final boolean strictEnd;
    private boolean valid = false;
    private byte[] curKey;
    /**
     * Constructs an iterator restricted to a range. Unlike the RocksDB standard iterators, 
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
     *  To scan all records that start with a prefix, you can use rangeStart=rangeStop=prefix , strictStart=strictStop=false
     *  
     */
    public AscendingRangeIterator(RocksIterator it, byte[] rangeStart, boolean strictStart, byte[] rangeStop, boolean strictStop) {
        this.iterator = it;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeStop;
        this.strictStart = strictStart;
        this.strictEnd = strictStop;       
        init();
    }

    private void init() {
        boolean startFound = false;
        valid = false;

        if(rangeStart==null) {
            iterator.seekToFirst();
            if(iterator.isValid()) {
                curKey = iterator.key();
                startFound = true;
            }
        } else {
            iterator.seek(rangeStart);
            if(iterator.isValid()) {
                curKey = iterator.key();

                if(strictStart && ByteArrayUtils.startsWith(curKey, rangeStart)) {
                    iterator.next();
                    if(iterator.isValid()) {
                        curKey = iterator.key();
                        startFound = true;
                    }
                } else {
                    startFound = true;
                }
            }
        }
        if(startFound) {
            //check that it is not beyond the end
            if(rangeEnd!=null) {
                int c = ByteArrayUtils.compare(curKey, rangeEnd);
                if((strictEnd && c<0) || (!strictEnd && c<=0)) {
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
        if(!valid) {
            throw new IllegalStateException("iterator is not valid");
        }

        iterator.next();
        if(iterator.isValid()) {
            curKey = iterator.key();
            if(rangeEnd!=null) {
                int c = ByteArrayUtils.compare(curKey, rangeEnd);
                if((strictEnd && c>=0) || (c>0)) {
                    valid = false;
                }
            }
        } else {
            valid = false;
        }
    }

    public byte[] key() {
        if(!valid) {
            throw new IllegalStateException("iterator is not valid");
        }
        return curKey;
    }

    public byte[] value() {
        if(!valid) {
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
