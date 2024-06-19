package org.yamcs.yarch.rocksdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.AbstractTableWalker;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.RawTuple;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class RdbTableWalker extends AbstractTableWalker {
    private final Tablespace tablespace;
    private final RdbTable table;
    static AtomicInteger count = new AtomicInteger(0);

    boolean batchUpdates = false;
    protected TableVisitor visitor;

    protected RdbTableWalker(ExecutionContext ctx, TableDefinition tableDefinition,
            boolean ascending, boolean follow) {
        super(ctx, tableDefinition, ascending, follow);

        this.tablespace = ctx.getTablespace();
        this.table = tablespace.getTable(tableDefinition);
    }

    /**
     * 
     * Iterate data through the given interval taking into account also the tableRange.
     * <p>
     * tableRange has to be non-null but can be unbounded at one or both ends.
     * <p>
     * Return true if the tableRange is bounded and the end has been reached.
     * 
     * @throws StreamSqlException
     */
    @Override
    protected boolean walkInterval(PartitionManager.Interval interval, DbRange tableRange, TableVisitor visitor)
            throws YarchException, StreamSqlException {
        this.visitor = visitor;
        running = true;
        try {
            return doWalkInterval(interval, tableRange);
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
     * @throws StreamSqlException
     */
    private boolean doWalkInterval(PartitionManager.Interval interval, DbRange tableRange)
            throws RocksDBException, StreamSqlException {
        DbIterator iterator = null;

        RdbPartition p1 = (RdbPartition) interval.iterator().next();
        final YRDB rdb;
        if (p1.dir != null) {
            log.debug("opening database {}", p1.dir);
            rdb = tablespace.getRdb(p1.dir, false);
        } else {
            rdb = tablespace.getRdb();
        }
        ReadOptions readOptions = new ReadOptions();

        readOptions.setTailing(follow);
        if (!follow) {
            Snapshot snapshot = ctx.getSnapshot(rdb);
            readOptions.setSnapshot(snapshot);
        }
        WriteBatch writeBatch = batchUpdates ? new WriteBatch() : null;
        var cfh = rdb.getColumnFamilyHandle(table.cfName());

        try {
            List<DbIterator> itList = new ArrayList<>(interval.size());
            // create an iterator for each partitions
            for (Partition p : interval) {
                p1 = (RdbPartition) p;
                if (!ascending) {
                    readOptions.setTotalOrderSeek(true);
                }
                RocksIterator rocksIt = rdb.getDb().newIterator(cfh, readOptions);
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
                endReached = runAscending(rdb, cfh, iterator, writeBatch, tableRange.rangeEnd);
            } else {
                endReached = runDescending(rdb, cfh, iterator, writeBatch, tableRange.rangeStart);
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
            readOptions.close();

            tablespace.dispose(rdb);

            if (writeBatch != null) {
                writeBatch.close();
            }
        }
    }

    // return true if the end condition has been reached
    boolean runAscending(YRDB rdb, ColumnFamilyHandle cfh, DbIterator iterator, WriteBatch writeBatch, byte[] rangeEnd)
            throws RocksDBException, StreamSqlException {

        while (isRunning() && iterator.isValid()) {
            byte[] dbKey = iterator.key();
            byte[] key = Arrays.copyOfRange(dbKey, 4, dbKey.length);
            byte[] value = iterator.value();
            numRecordsRead++;

            if (iAscendingFinished(key, value, rangeEnd)) {
                return true;
            }
            TableVisitor.Action action = visitor.visit(key, iterator.value());
            if (writeBatch == null) {
                executeAction(rdb, cfh, action, dbKey);
            } else {
                executeAction(rdb, cfh, writeBatch, action, dbKey);
            }
            if (action.stop()) {
                close();
                return false;
            }

            iterator.next();
        }
        return false;
    }

    boolean runDescending(YRDB rdb, ColumnFamilyHandle cfh, DbIterator iterator, WriteBatch writeBatch,
            byte[] rangeStart)
            throws RocksDBException, StreamSqlException {
        while (isRunning() && iterator.isValid()) {
            byte[] dbKey = iterator.key();
            byte[] key = Arrays.copyOfRange(dbKey, 4, dbKey.length);
            numRecordsRead++;

            if (isDescendingFinished(key, iterator.value(), rangeStart)) {
                return true;
            }

            TableVisitor.Action action = visitor.visit(key, iterator.value());
            if (writeBatch == null) {
                executeAction(rdb, cfh, action, dbKey);
            } else {
                executeAction(rdb, cfh, writeBatch, action, dbKey);
            }

            if (action.stop()) {
                close();
                return false;
            }
            iterator.prev();
        }
        return false;
    }

    static void executeAction(YRDB rdb, ColumnFamilyHandle cfh, TableVisitor.Action action, byte[] dbKey)
            throws RocksDBException, StreamSqlException {
        if (action.action() == TableVisitor.ActionType.DELETE) {
            rdb.delete(cfh, dbKey);
        } else if (action.action() == TableVisitor.ActionType.UPDATE_VAL) {
            rdb.put(cfh, dbKey, action.getUpdatedValue());
        } else if (action.action() == TableVisitor.ActionType.UPDATE_ROW) {
            // we only support updates on non partition tables
            int tbsIndex = RdbStorageEngine.tbsIndex(dbKey);
            byte[] updatedDbKey = RdbStorageEngine.dbKey(tbsIndex, action.getUpdatedKey());
            if (rdb.get(cfh, updatedDbKey) != null) {
                throw new StreamSqlException(ErrCode.DUPLICATE_KEY,
                        "duplicate key in update: " + StringConverter.arrayToHexString(updatedDbKey));
            }

            rdb.delete(cfh, dbKey);
            rdb.put(cfh, updatedDbKey, action.getUpdatedValue());

        }
    }

    static void executeAction(YRDB rdb, ColumnFamilyHandle cfh, WriteBatch writeBatch, TableVisitor.Action action,
            byte[] dbKey)
            throws RocksDBException, StreamSqlException {
        if (action.action() == TableVisitor.ActionType.DELETE) {
            rdb.delete(cfh, dbKey);
        } else if (action.action() == TableVisitor.ActionType.UPDATE_VAL) {
            rdb.put(cfh, dbKey, action.getUpdatedValue());
        } else if (action.action() == TableVisitor.ActionType.UPDATE_ROW) {
            // we only support updates on non partition tables
            int tbsIndex = RdbStorageEngine.tbsIndex(dbKey);
            byte[] updatedDbKey = RdbStorageEngine.dbKey(tbsIndex, action.getUpdatedKey());
            if (rdb.get(cfh, updatedDbKey) != null) {
                throw new StreamSqlException(ErrCode.DUPLICATE_KEY,
                        "duplicate key in update: " + StringConverter.arrayToHexString(updatedDbKey));
            }
            rdb.delete(cfh, dbKey);
            rdb.put(cfh, updatedDbKey, action.getUpdatedValue());
        }
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

    public boolean isBatchUpdates() {
        return batchUpdates;
    }

    public void setBatchUpdates(boolean batchUpdates) {
        this.batchUpdates = batchUpdates;
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

    static DbRange getDbRange(int tbsIndex, DbRange tableRange) {
        DbRange dbr = new DbRange();
        if (tableRange != null && tableRange.rangeStart != null) {
            dbr.rangeStart = RdbStorageEngine.dbKey(tbsIndex, tableRange.rangeStart);
        } else {
            dbr.rangeStart = RdbStorageEngine.dbKey(tbsIndex);
        }

        if (tableRange != null && tableRange.rangeEnd != null) {
            dbr.rangeEnd = RdbStorageEngine.dbKey(tbsIndex, tableRange.rangeEnd);
        } else {
            dbr.rangeEnd = RdbStorageEngine.dbKey(tbsIndex);
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
