package org.yamcs.yarch.rocksdb;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Sequence;
import org.yamcs.yarch.YarchException;

/**
 * sequence stored in the Rocksdb metadata.
 * <p>
 * The sequence will "cache" some numbers such that it does not have to update the database after each
 * increment. If the system crashes, the numbers cached will be lost.
 * 
 * @author nm
 *
 */
public class RdbSequence implements Sequence {
    static final int CACHE_SIZE = 100;
    YRDB rdb;
    ColumnFamilyHandle cfMetadata;

    final byte[] dbkey;

    // the numbers are given by the seq atomic long up to the limit.
    // when the limit is reached we increment it after writing it to the database
    // negative number signals sequence closure (also done in case of errors)
    AtomicLong seq;
    volatile long limit;

    public RdbSequence(String name, YRDB rdb, ColumnFamilyHandle cfMetadata) throws RocksDBException, YarchException {
        this.rdb = rdb;
        this.cfMetadata = cfMetadata;
        dbkey = getDbKey(name);
        long n = 0;
        byte[] v = rdb.get(cfMetadata, dbkey);
        if (v != null) {
            n = getValue(v);
        }
        seq = new AtomicLong(n);
        limit = n + CACHE_SIZE;
        rdb.put(cfMetadata, dbkey, ByteArrayUtils.encodeLong(limit));
    }

    @Override
    public long next() throws YarchException {
        long _limit = limit;
        long n = seq.getAndIncrement();
        
        if (n < 0) {
            throw new YarchException("sequence is closed");
        }
        
        if (n >= _limit) {
            while (n >= limit) {
                increaseLimit(n);
                if (seq.get() < 0) {
                    // this check is required because if the increaseLimit would throw an exception in a different
                    // thread
                    // n could be invalid.
                    throw new YarchException("sequence is closed");
                }
                if (limit < _limit) { // there has been a reset
                    return next();
                }
            }
        }
        return n;
    }

    private synchronized void increaseLimit(long n) throws YarchException {
        if (n < limit) {
            // if multiple threads call increaseLimit at the same time, the limit may have already been increased
            return;
        }

        long nlimit = n + CACHE_SIZE;
        try {
            rdb.put(cfMetadata, dbkey, ByteArrayUtils.encodeLong(nlimit));
        } catch (RocksDBException e) {
            seq.set(Long.MIN_VALUE);
            throw new YarchException(e);
        }

        limit = nlimit;
    }

    public synchronized void close() throws YarchException {
        long n = seq.getAndSet(Long.MIN_VALUE);
        if (n < 0) {// already closed
            return;
        }

        try {
            rdb.put(cfMetadata, dbkey, ByteArrayUtils.encodeLong(n));
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public synchronized void reset(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("sequence value should be positive");
        }
        if (seq.get() < 0) {
            throw new YarchException("sequence is closed");
        }

        long nlimit = value + CACHE_SIZE;

        try {
            rdb.put(cfMetadata, dbkey, ByteArrayUtils.encodeLong(nlimit));
        } catch (RocksDBException e) {
            seq.set(Long.MIN_VALUE);
            throw new YarchException(e);
        }

        limit = nlimit;
        seq.set(value);
    }

    /**
     * Return the current sequence value
     * 
     * @return
     */
    @Override
    public long get() {
        return seq.get();
    }

    public static String getName(byte[] dbkey) {
        return new String(dbkey, 1, dbkey.length - 1, StandardCharsets.UTF_8);
    }

    public static byte[] getDbKey(String name) {
        byte[] nb = name.getBytes(StandardCharsets.UTF_8);
        byte[] dbkey = new byte[nb.length + 1];

        dbkey[0] = Tablespace.METADATA_FB_SEQ;
        System.arraycopy(nb, 0, dbkey, 1, nb.length);
        return dbkey;
    }

    public static long getValue(byte[] value) {
        if (value.length != 8) {
            throw new YarchException("Sequence value length is not 8 but " + value.length);
        }
        return ByteArrayUtils.decodeLong(value, 0);
    }

}
