package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.FlushOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.AbstractTableWalker;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.IndexFilter;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.RawTuple;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * reader for tables with PartitionStorage.IN_KEY (the partition is prepended in front of the key)
 * 
 * @author nm
 *
 */
public class RdbTableWalker extends AbstractTableWalker {
    static AtomicInteger count = new AtomicInteger(0);
    final PartitioningSpec partitioningSpec;
    private long numRecordsRead = 0;
    private final Tablespace tablespace;
    boolean batchUpdates = false;

    protected RdbTableWalker(Tablespace tablespace, YarchDatabaseInstance ydb, PartitionManager pmgr,
            boolean ascending, boolean follow) {
        super(ydb, pmgr, ascending, follow);

        this.tablespace = tablespace;
        partitioningSpec = pmgr.getPartitioningSpec();
    }

    /**
     * reads a file, sending data only that conform with the start and end filters.
     * returns true if the stop condition is met
     * 
     * All the partitions are from the same time interval and thus from one single RocksDB database
     * 
     */
    @Override
    protected boolean walkPartitions(List<Partition> partitions, IndexFilter filter) throws YarchException {
        DbRange tableRange = getTableRange(filter);

        try {
            return walkValuePartitions(partitions, tableRange);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    /**
     * runs value based partitions: the partition value is encoded as the first bytes of the key, so we have to make
     * multiple parallel iterators
     *
     * @return true if the end condition has been reached
     * @throws RocksDBException
     */
    private boolean walkValuePartitions(List<Partition> partitions, DbRange tableRange) throws RocksDBException {
        DbIterator iterator = null;

        RdbPartition p1 = (RdbPartition) partitions.get(0);
        YRDB rdb;
        if (p1.dir != null) {
            try {
                log.debug("opening database {}", p1.dir);
                rdb = tablespace.getRdb(p1.dir, false);
            } catch (IOException e) {
                log.error("Failed to open database", e);
                return false;
            }
        } else {
            rdb = tablespace.getRdb();
        }

        ReadOptions readOptions = new ReadOptions();
        readOptions.setTailing(follow);
        Snapshot snapshot = null;
        if (!follow) {
            snapshot = rdb.getDb().getSnapshot();
            readOptions.setSnapshot(snapshot);
        }
        WriteBatch writeBatch = batchUpdates ? new WriteBatch() : null;

        try {
            List<DbIterator> itList = new ArrayList<>(partitions.size());
            // create an iterator for each partitions
            for (Partition p : partitions) {
                p1 = (RdbPartition) p;
                RocksIterator rocksIt = rdb.getDb().newIterator(readOptions);
                DbIterator it = getPartitionIterator(rocksIt, p1.tbsIndex, ascending, tableRange);

                if (it.isValid()) {
                    itList.add(it);
                } else {
                    it.close();
                }
            }
            if (itList.size() == 0) {
                return false;
            } else if (itList.size() == 1) {
                iterator = itList.get(0);
            } else {
                iterator = new MergingIterator(itList,
                        ascending ? new SuffixAscendingComparator(4) : new SuffixDescendingComparator(4));
            }
            boolean endReached;
            if (ascending) {
                endReached = runAscending(rdb, iterator, writeBatch, tableRange.rangeEnd, tableRange.strictEnd);
            } else {
                endReached = runDescending(rdb, iterator, writeBatch, tableRange.rangeStart, tableRange.strictStart);
            }
            if (writeBatch != null) {
                WriteOptions wo = new WriteOptions();
                rdb.getDb().write(wo, writeBatch);
                wo.close();
            }
            return endReached;
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            if (snapshot != null) {
                snapshot.close();
            }
            readOptions.close();
            tablespace.dispose(rdb);

            if (writeBatch != null) {
                writeBatch.close();
            }
        }
    }

    // return true if the end condition has been reached
    boolean runAscending(YRDB rdb, DbIterator iterator, WriteBatch writeBatch, byte[] rangeEnd, boolean strictEnd)
            throws RocksDBException {
        while (isRunning() && iterator.isValid()) {
            byte[] dbKey = iterator.key();
            byte[] key = Arrays.copyOfRange(dbKey, 4, dbKey.length);
            byte[] value = iterator.value();
            if (iAscendingFinished(key, value, rangeEnd, strictEnd)) {
                return true;
            }

            TableVisitor.Action action = visitor.visit(key, iterator.value());
            executeAction(rdb, action, dbKey);

            if (!isRunning()) {
                return false;
            }

            iterator.next();
        }
        return false;
    }

    boolean runDescending(YRDB rdb, DbIterator iterator, WriteBatch writeBatch, byte[] rangeStart, boolean strictStart)
            throws RocksDBException {
        while (isRunning() && iterator.isValid()) {
            byte[] dbKey = iterator.key();
            byte[] key = Arrays.copyOfRange(dbKey, 4, dbKey.length);
            if (isDescendingFinished(key, iterator.value(), rangeStart, strictStart)) {
                return true;
            }

            TableVisitor.Action action = visitor.visit(key, iterator.value());
            executeAction(rdb, action, dbKey);

            if (!isRunning()) {
                return false;
            }
            iterator.prev();
        }
        return false;
    }

    void executeAction(WriteBatch writeBatch, TableVisitor.Action action, byte[] dbKey)
            throws RocksDBException {
        if (action.action() == TableVisitor.ActionType.DELETE) {
            writeBatch.delete(dbKey);
        } else if (action.action() == TableVisitor.ActionType.UPDATE) {
            writeBatch.delete(dbKey);
        }
        if (action.stop()) {
            close();
        }
    }

    void executeAction(YRDB rdb, TableVisitor.Action action, byte[] dbKey)
            throws RocksDBException {
        if (action.action() == TableVisitor.ActionType.DELETE) {
            rdb.delete(dbKey);
        } else if (action.action() == TableVisitor.ActionType.UPDATE) {
            rdb.put(dbKey, action.getUpdateValue());
        }
        if (action.stop()) {
            close();
        }
    }

    public void setBatchUpdates(boolean batchUpdates) {
        this.batchUpdates = batchUpdates;
    }

    /*
     * create a ranging iterator for the given partition
     * TODO: check usage of RocksDB prefix iterators
     * 
     */
    private DbIterator getPartitionIterator(RocksIterator it, int tbsIndex, boolean ascending, DbRange tableRange) {
        DbRange dbRange = getDbRange(tbsIndex, tableRange);
        if (ascending) {
            return new AscendingRangeIterator(it, dbRange);
        } else {
            return new DescendingRangeIterator(it, dbRange);
        }
    }

    public long getNumRecordsRead() {
        return numRecordsRead;
    }

    @Override
    protected boolean bulkDeleteFromPartitions(List<Partition> partitions, IndexFilter filter) throws YarchException {
        DbRange tableRange = getTableRange(filter);

        // all partitions will have the same database, just use the same one
        RdbPartition p1 = (RdbPartition) partitions.get(0);

        YRDB rdb;
        try {
            rdb = tablespace.getRdb(p1.dir, false);
        } catch (IOException e) {
            log.error("Failed to open database", e);
            throw new YarchException(e);
        }

        try (FlushOptions flushOptions = new FlushOptions()) {
            for (Partition p : partitions) {
                RdbPartition rp = (RdbPartition) p;
                DbRange dbRange = getDeleteDbRange(rp.tbsIndex, tableRange);
                rdb.getDb().deleteRange(dbRange.rangeStart, dbRange.rangeEnd);
            }

            rdb.getDb().flush(flushOptions);

        } catch (RocksDBException e) {
            throw new YarchException(e);
        } finally {
            tablespace.dispose(rdb);
        }
        return false;
    }

    class RdbRawTuple extends RawTuple {
        RocksIterator iterator;
        byte[] partition;
        byte[] key;
        byte[] value;

        public RdbRawTuple(byte[] partition, byte[] key, byte[] value, RocksIterator iterator, int index) {
            super(index);
            this.partition = partition;
            this.key = key;
            this.value = value;
            this.iterator = iterator;
        }

        @Override
        protected byte[] getKey() {
            return key;
        }

        @Override
        protected byte[] getValue() {
            return value;
        }
    }

    private DbRange getTableRange(IndexFilter filter) {
        DbRange tableRange = new DbRange();
        if (filter != null) {
            ColumnDefinition cd = tableDefinition.getKeyDefinition().get(0);
            ColumnSerializer cs = tableDefinition.getColumnSerializer(cd.getName());
            if (filter.keyStart != null) {
                tableRange.strictStart = filter.strictStart;
                tableRange.rangeStart = cs.toByteArray(filter.keyStart);
            }
            if (filter.keyEnd != null) {
                tableRange.strictEnd = filter.strictEnd;
                tableRange.rangeEnd = cs.toByteArray(filter.keyEnd);
            }
        }
        return tableRange;
    }

    DbRange getDbRange(int tbsIndex, DbRange tableRange) {
        DbRange dbr = new DbRange();
        if (tableRange.rangeStart != null) {
            dbr.rangeStart = RdbStorageEngine.dbKey(tbsIndex, tableRange.rangeStart);
            dbr.strictStart = tableRange.strictStart;
        } else {
            dbr.rangeStart = RdbStorageEngine.dbKey(tbsIndex);
            dbr.strictStart = false;
        }

        if (tableRange.rangeEnd != null) {
            dbr.rangeEnd = RdbStorageEngine.dbKey(tbsIndex, tableRange.rangeEnd);
            dbr.strictEnd = tableRange.strictEnd;
        } else {
            dbr.rangeEnd = RdbStorageEngine.dbKey(tbsIndex + 1);
            dbr.strictEnd = true;
        }
        return dbr;
    }

    // rocksdb delete range intervals are always [start, end) so we have to adapt our range
    DbRange getDeleteDbRange(int tbsIndex, DbRange tableRange) {
        DbRange dbr = new DbRange();
        dbr.strictStart = false;
        dbr.strictEnd = true;

        if (tableRange.rangeStart == null) {
            dbr.rangeStart = tableRange.strictStart ? RdbStorageEngine.dbKey(tbsIndex + 1)
                    : RdbStorageEngine.dbKey(tbsIndex);
        } else {
            dbr.rangeStart = RdbStorageEngine.dbKey(tbsIndex, tableRange.rangeStart);
            if (dbr.strictStart) {
                dbr.rangeStart = ByteArrayUtils.plusOne(dbr.rangeStart);
            }
        }

        if (tableRange.rangeEnd == null) {
            dbr.rangeEnd = tableRange.strictEnd ? RdbStorageEngine.dbKey(tbsIndex)
                    : RdbStorageEngine.dbKey(tbsIndex + 1);
        } else {
            dbr.rangeEnd = RdbStorageEngine.dbKey(tbsIndex, tableRange.rangeEnd);
            if (!tableRange.strictEnd) {
                dbr.rangeEnd = ByteArrayUtils.plusOne(dbr.rangeEnd);
            }
        }
        return dbr;
    }

    static class SuffixAscendingComparator implements Comparator<byte[]> {
        int prefixSize;

        public SuffixAscendingComparator(int prefixSize) {
            this.prefixSize = prefixSize;
        }

        @Override
        public int compare(byte[] b1, byte[] b2) {
            int minLength = Math.min(b1.length, b2.length);
            for (int i = prefixSize; i < minLength; i++) {
                int d = (b1[i] & 0xFF) - (b2[i] & 0xFF);
                if (d != 0) {
                    return d;
                }
            }
            for (int i = 0; i < prefixSize; i++) {
                int d = (b1[i] & 0xFF) - (b2[i] & 0xFF);
                if (d != 0) {
                    return d;
                }
            }
            return b1.length - b2.length;
        }
    }

    static class SuffixDescendingComparator implements Comparator<byte[]> {
        int prefixSize;

        public SuffixDescendingComparator(int prefixSize) {
            this.prefixSize = prefixSize;
        }

        @Override
        public int compare(byte[] b1, byte[] b2) {
            int minLength = Math.min(b1.length, b2.length);
            for (int i = prefixSize; i < minLength; i++) {
                int d = (b2[i] & 0xFF) - (b1[i] & 0xFF);
                if (d != 0) {
                    return d;
                }
            }
            for (int i = 0; i < prefixSize; i++) {
                int d = (b2[i] & 0xFF) - (b1[i] & 0xFF);
                if (d != 0) {
                    return d;
                }
            }
            return b2.length - b1.length;
        }
    }
}
