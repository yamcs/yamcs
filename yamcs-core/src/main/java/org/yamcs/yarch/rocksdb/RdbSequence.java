package org.yamcs.yarch.rocksdb;

import java.util.concurrent.atomic.AtomicLong;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Sequence;
import org.yamcs.yarch.YarchException;

import com.google.common.base.Charsets;

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

    final byte[] dbkey;

    AtomicLong seq;
    volatile long limit;
    YRDB rdb;
    ColumnFamilyHandle cfMetadata;

    public RdbSequence(String name, YRDB rdb, ColumnFamilyHandle cfMetadata) throws RocksDBException, YarchException {
        byte[] nb = name.getBytes(Charsets.UTF_8);
        dbkey = new byte[nb.length + 1];

        dbkey[0] = Tablespace.METADATA_FB_SEQ;
        System.arraycopy(nb, 0, dbkey, 1, nb.length);

        this.rdb = rdb;
        this.cfMetadata = cfMetadata;

        long n = 0;
        byte[] v = rdb.get(cfMetadata, dbkey);
        if (v != null) {
            if (v.length != 8) {
                throw new YarchException("Sequence value length is not 8 but " + v.length);
            }
            n = ByteArrayUtils.decodeLong(v, 0);
        }
        seq = new AtomicLong(n-1);
        limit = n + CACHE_SIZE;
        rdb.put(cfMetadata, dbkey, ByteArrayUtils.encodeLong(limit));

    }

    @Override
    public long next() throws YarchException {
        long n = seq.incrementAndGet();

        while (n >= limit) {
            increaseLimit(n);
            if (seq.get() < 0) {
                // this check is required because if the increaseLimit would throw an exception in a different thread
                // without increasing the limit, we would go here in an infinite loop. 
                throw new YarchException("sequence is closed");
            }
        }
        if (n < 0) {
            throw new YarchException("sequence is closed");
        }
        return n;
    }

    private synchronized void increaseLimit(long n) throws YarchException {
        if (n != limit) {
            return;
        }

        long nlimit = limit + CACHE_SIZE;
        try {
            rdb.put(cfMetadata, dbkey, ByteArrayUtils.encodeLong(nlimit));
        } catch (RocksDBException e) {
            seq.set(Long.MIN_VALUE);
            throw new YarchException(e);
        }

        limit = nlimit;

    }

    synchronized void close() throws RocksDBException {
        long n = seq.getAndSet(Long.MIN_VALUE);
        if (n < 0) {// already closed
            return;
        }

        rdb.put(cfMetadata, dbkey, ByteArrayUtils.encodeLong(n+1));
    }
}
