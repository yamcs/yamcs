package org.yamcs.yarch.rocksdb;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.StreamSqlException;

/** Ordered pointer scan in the main DB, with point reads in the source partitions. */
public class SecondaryIndexTableWalker implements TableWalker {
    private final RdbTable table;
    private final Tablespace tablespace;
    private final boolean ascending;
    private DbRange skRange = new DbRange();
    private DbRange pkRange;
    private byte[] resumeAfter;
    private byte[] stopBefore;
    private byte[] lastKey;
    private Long expectedBuildRevision;
    private Set<Integer> partitions;
    private final Map<Integer, RdbPartition> sourcePartitions = new HashMap<>();
    private boolean batchUpdates;
    private volatile boolean running = true;
    private long numRecordsRead;
    private long numPayloadReads;

    protected SecondaryIndexTableWalker(Tablespace tablespace, RdbTable table, boolean ascending, boolean follow) {
        this.tablespace = tablespace;
        this.table = table;
        this.ascending = ascending;
        for (var p : table.partitionManager.getPartitions()) {
            var partition = (RdbPartition) p;
            sourcePartitions.put(partition.tbsIndex, partition);
        }
        if (follow) {
            throw new UnsupportedOperationException("Following a secondary index is not supported");
        }
    }

    /** Full encoded key suffix, excluded from the next page. */
    public void setResumeAfter(byte[] key) {
        resumeAfter = key;
    }

    public void setExpectedBuildRevision(long revision) {
        expectedBuildRevision = revision;
    }

    /** Exclusive upper prefix, for example reception time stop. */
    public void setStopBefore(byte[] prefix) {
        stopBefore = prefix;
    }

    public byte[] getLastKey() {
        return lastKey == null ? null : lastKey.clone();
    }

    public long getNumRecordsRead() {
        return numRecordsRead;
    }

    public long getNumPayloadReads() {
        return numPayloadReads;
    }

    @Override
    public void setPartitionFilter(TimeInterval time, Set<Object> values) {
        partitions = new HashSet<>();
        for (var p : table.partitionManager.getPartitions()) {
            if ((values == null || values.contains(p.getValue()))
                    && (time == null || ((!time.hasStart() || p.getEnd() > time.getStart())
                            && (!time.hasEnd() || p.getStart() <= time.getEnd())))) {
                partitions.add(((RdbPartition) p).tbsIndex);
            }
        }
    }

    @Override
    public void walk(TableVisitor visitor) throws YarchException, StreamSqlException {
        table.indexLock.readLock().lock();
        try {
            SecondaryIndexWriter writer = table.getSecondaryIndexWriter();
            writer.requireReady();
            if (expectedBuildRevision != null && expectedBuildRevision != writer.getInfo().getBuildRevision()) {
                throw new YarchException("Secondary index revision changed; restart the query");
            }
            DbRange range = new DbRange();
            range.rangeStart = skRange.rangeStart;
            range.rangeEnd = skRange.rangeEnd;
            if (resumeAfter != null) {
                if (ascending && (range.rangeStart == null || Arrays.compareUnsigned(resumeAfter, range.rangeStart) > 0)) {
                    range.rangeStart = resumeAfter;
                } else if (!ascending && (range.rangeEnd == null
                        || Arrays.compareUnsigned(resumeAfter, range.rangeEnd) < 0)) {
                    range.rangeEnd = resumeAfter;
                }
            }
            DbRange dbRange = RdbTableWalker.getDbRange(writer.getTbsIndex(), range);
            YRDB main = tablespace.getRdb();
            try (ReadOptions options = new ReadOptions().setTotalOrderSeek(true);
                    RocksIterator rocksIt = main.getDb().newIterator(options);
                    DbIterator it = ascending ? new AscendingRangeIterator(rocksIt, dbRange)
                            : new DescendingRangeIterator(rocksIt, dbRange)) {
                while (running && it.isValid()) {
                    byte[] key = it.key();
                    byte[] suffix = Arrays.copyOfRange(key, 4, key.length);
                    if ((resumeAfter == null || Arrays.compareUnsigned(suffix, resumeAfter) != 0)
                            && (stopBefore == null || ByteArrayUtils.compare(suffix, stopBefore) < 0)) {
                        numRecordsRead++;
                        byte[] pk = getPk(key);
                        if (pkInRange(pk)) {
                            visitRow(visitor, pk, suffix, it.value());
                        }
                    }
                    if (!running) {
                        break;
                    }
                    if (ascending) {
                        it.next();
                    } else {
                        it.prev();
                    }
                }
                rocksIt.status();
            } catch (RocksDBException e) {
                throw new YarchException(e);
            }
        } finally {
            table.indexLock.readLock().unlock();
        }
    }

    private void visitRow(TableVisitor visitor, byte[] pk, byte[] suffix, byte[] pointer)
            throws StreamSqlException, RocksDBException {
        int partitionId = RdbStorageEngine.tbsIndex(pointer);
        if (partitions != null && !partitions.contains(partitionId)) {
            return;
        }
        RdbPartition partition = sourcePartitions.get(partitionId);
        if (partition == null) {
            return;
        }
        String dir = pointer.length == 4 ? null
                : new String(pointer, 4, pointer.length - 4, StandardCharsets.US_ASCII);
        YRDB db = tablespace.getRdb(dir, false);
        try {
            var cf = db.getColumnFamilyHandle(table.cfName());
            byte[] rowKey = RdbStorageEngine.dbKey(partitionId, pk);
            byte[] value = db.get(cf, rowKey);
            numPayloadReads++;
            if (value != null) {
                if (!Arrays.equals(suffix, SecondaryIndexKey.suffix(table.getDefinition(),
                        table.getDefinition().deserialize(pk, value)))) {
                    return;
                }
                lastKey = suffix;
                TableVisitor.Action action = visitor.visit(pk, value);
                table.executeAction(db, cf, partition, rowKey, action);
                if (action.stop()) {
                    close();
                }
            }
        } finally {
            tablespace.dispose(db);
        }
    }

    private boolean pkInRange(byte[] pk) {
        return pkRange == null
                || ((pkRange.rangeStart == null || ByteArrayUtils.compare(pk, pkRange.rangeStart) >= 0)
                        && (pkRange.rangeEnd == null || ByteArrayUtils.compare(pk, pkRange.rangeEnd) <= 0));
    }

    byte[] getPk(byte[] key) {
        return SecondaryIndexKey.primaryKey(table.getDefinition(), key);
    }

    @Override
    public void setPrimaryIndexRange(DbRange range) {
        pkRange = range;
    }

    @Override
    public void setSecondaryIndexRange(DbRange range) {
        skRange = range == null ? new DbRange() : range;
    }

    @Override
    public void close() {
        running = false;
    }

    @Override
    public boolean isBatchUpdates() {
        return batchUpdates;
    }

    @Override
    public void setBatchUpdates(boolean value) {
        batchUpdates = value;
    }
}
