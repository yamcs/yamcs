package org.yamcs.yarch.rocksdb;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.yamcs.utils.ByteArray;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.PendingIndexOperation;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndex;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndex.State;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndexCheckpoint;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

import com.google.protobuf.ByteString;

/** Maintains a global pointer index, with a durable outbox in each source database. */
public class SecondaryIndexWriter {
    static final String PENDING_CF = "_secondary_index_pending";
    private final RdbTable table;
    private final Tablespace tablespace;
    private final TableDefinition definition;
    private volatile TablespaceRecord record;
    private volatile CompletableFuture<Void> build;
    private volatile boolean closed;
    private volatile boolean preparingBuild;
    // Package-private fault injection points exercised by recovery tests.
    Runnable afterSourceCommit = () -> { };
    Runnable afterIndexCommit = () -> { };
    Runnable beforeBackfillRow = () -> { };

    SecondaryIndexWriter(RdbTable table, TablespaceRecord record) {
        this.table = table;
        this.tablespace = table.tablespace;
        this.definition = table.getDefinition();
        this.record = record;
    }

    public int getTbsIndex() {
        return record.getTbsIndex();
    }

    public SecondaryIndex getInfo() {
        SecondaryIndex info = record.getSecondaryIndex();
        CompletableFuture<Void> runningBuild = build;
        if (preparingBuild || (runningBuild != null && !runningBuild.isDone() && info.getState() == State.READY)) {
            return info.toBuilder().setManaged(true).setState(State.BUILDING).clearFailure().build();
        }
        return info;
    }

    public void requireReady() {
        if (getInfo().getState() != State.READY) {
            throw new YarchException("Secondary index is " + getInfo().getState());
        }
    }

    private byte[] indexKey(byte[] rowKey, byte[] value) {
        if (value == null) {
            return null;
        }
        Tuple tuple = definition.deserialize(Arrays.copyOfRange(rowKey, 4, rowKey.length), value);
        if (getInfo().getManaged() && definition.getSecondaryIndex().equals(java.util.List.of("rectime", "pname"))
                && tuple.getColumn("rectime") == null) {
            return null;
        }
        return RdbStorageEngine.dbKey(getTbsIndex(), SecondaryIndexKey.suffix(definition, tuple));
    }

    private byte[] pointer(RdbPartition partition) {
        ByteArray value = new ByteArray();
        value.addInt(partition.tbsIndex);
        if (partition.dir != null) {
            value.add(partition.dir.getBytes(StandardCharsets.US_ASCII));
        }
        return value.toArray();
    }

    /** Caller holds the lifecycle read lock and source-row lock. */
    void write(YRDB db, ColumnFamilyHandle cf, RdbPartition partition,
            byte[] oldRowKey, byte[] newRowKey, byte[] value) throws RocksDBException {
        try {
            if (db != tablespace.getRdb()) {
                replayRow(db, oldRowKey);
            }
            byte[] oldKey = indexKey(oldRowKey, db.get(cf, oldRowKey));
            byte[] newKey = indexKey(newRowKey, value);
            PendingIndexOperation.Builder op = PendingIndexOperation.newBuilder()
                    .setOperationId(UUID.randomUUID().toString());
            if (oldKey != null) {
                op.setDeleteKey(ByteString.copyFrom(oldKey));
            }
            if (newKey != null) {
                op.setPutKey(ByteString.copyFrom(newKey)).setPutValue(ByteString.copyFrom(pointer(partition)));
            }
            try (WriteBatch batch = new WriteBatch();
                    WriteOptions options = new WriteOptions().setSync(db != tablespace.getRdb())) {
                if (value == null || !Arrays.equals(oldRowKey, newRowKey)) {
                    batch.delete(cf, oldRowKey);
                }
                if (value != null) {
                    batch.put(cf, newRowKey, value);
                }
                if (db == tablespace.getRdb()) {
                    addOperation(batch, op.build());
                    db.write(options, batch);
                } else {
                    var pending = db.createAndGetColumnFamilyHandle(PENDING_CF);
                    byte[] journalKey = RdbStorageEngine.dbKey(getTbsIndex(), oldRowKey);
                    batch.put(pending, journalKey, op.build().toByteArray());
                    db.write(options, batch);
                    afterSourceCommit.run();
                    apply(op.build());
                    afterIndexCommit.run();
                    // apply() syncs the main WAL before the source journal is acknowledged.
                    db.delete(pending, journalKey);
                }
            }
        } catch (RocksDBException | RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    private void addOperation(WriteBatch batch, PendingIndexOperation op) throws RocksDBException {
        if (op.hasDeleteKey()) {
            batch.delete(op.getDeleteKey().toByteArray());
        }
        if (op.hasPutKey()) {
            batch.put(op.getPutKey().toByteArray(), op.getPutValue().toByteArray());
        }
    }

    private void apply(PendingIndexOperation op) throws RocksDBException {
        try (WriteBatch batch = new WriteBatch(); WriteOptions options = new WriteOptions().setSync(true)) {
            addOperation(batch, op);
            tablespace.getRdb().write(options, batch);
        }
    }

    private void replayRow(YRDB db, byte[] rowKey) throws RocksDBException {
        var cf = db.getColumnFamilyHandle(PENDING_CF);
        if (cf == null) {
            return;
        }
        byte[] key = RdbStorageEngine.dbKey(getTbsIndex(), rowKey);
        byte[] value = db.get(cf, key);
        if (value != null) {
            try {
                apply(PendingIndexOperation.parseFrom(value));
                db.delete(cf, key);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new YarchException("Invalid pending secondary-index operation", e);
            }
        }
    }

    /** Recovery completes before writers start, and also before a resumed build becomes ready. */
    void recover() {
        table.indexLock.writeLock().lock();
        SecondaryIndex info = getInfo();
        try {
            setInfo(info.toBuilder().setState(State.BUILDING));
            byte[] prefix = RdbStorageEngine.dbKey(getTbsIndex());
            for (var p : table.partitionManager.getPartitions()) {
                RdbPartition partition = (RdbPartition) p;
                if (partition.dir == null) {
                    continue;
                }
                YRDB db = tablespace.getRdb(partition.dir, false);
                try {
                    var cf = db.getColumnFamilyHandle(PENDING_CF);
                    if (cf == null) {
                        continue;
                    }
                    try (AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(cf), prefix, prefix)) {
                        while (it.isValid()) {
                            byte[] rowKey = Arrays.copyOfRange(it.key(), 4, it.key().length);
                            if (RdbStorageEngine.tbsIndex(rowKey) == partition.tbsIndex) {
                                db.lock(rowKey);
                                try {
                                    replayRow(db, rowKey);
                                } finally {
                                    db.unlock(rowKey);
                                }
                            }
                            it.next();
                        }
                    }
                } finally {
                    tablespace.dispose(db);
                }
            }
            setInfo(info.toBuilder());
        } catch (Exception e) {
            fail(e);
        } finally {
            table.indexLock.writeLock().unlock();
        }
    }

    synchronized void setInfo(SecondaryIndex.Builder info) throws RocksDBException {
        TablespaceRecord updated = record.toBuilder().setSecondaryIndex(info).build();
        try (WriteBatch batch = new WriteBatch(); WriteOptions options = new WriteOptions().setSync(true)) {
            try {
                tablespace.writeToBatch(table.yamcsInstance, batch, updated.toBuilder());
            } catch (java.io.IOException e) {
                throw new YarchException(e);
            }
            tablespace.getRdb().write(options, batch);
        }
        record = updated;
    }

    void fail(Throwable e) {
        try {
            setInfo(getInfo().toBuilder().setState(State.FAILED)
                    .setFailure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } catch (Exception persistenceError) {
            record = record.toBuilder().setSecondaryIndex(getInfo().toBuilder().setState(State.FAILED)
                    .setFailure("Index failure; unable to persist state")).build();
        }
    }

    public synchronized CompletableFuture<Void> rebuild(boolean fresh) {
        if (build != null && !build.isDone()) {
            return build;
        }
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Index is closed"));
        }
        build = new CompletableFuture<>();
        preparingBuild = true;
        CompletableFuture<Void> result = build;
        try {
            tablespace.getExecutor().execute(() -> {
                try {
                    prepareBuild(fresh);
                    backfill();
                    result.complete(null);
                } catch (Throwable e) {
                    if (!closed) {
                        fail(e);
                    }
                    result.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) {
            preparingBuild = false;
            fail(e);
            result.completeExceptionally(e);
        }
        return result;
    }

    private void prepareBuild(boolean fresh) throws Exception {
        // This barrier may wait for active exports. It must never run on the HTTP event loop.
        table.indexLock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("Index build stopped");
            }
            SecondaryIndex.Builder info = record.getSecondaryIndex().toBuilder()
                    .setManaged(true).setState(State.BUILDING).clearFailure();
            if (fresh) {
                info.setBuildRevision(info.getBuildRevision() + 1).setProcessedRows(0).setMissingRows(0).clearCheckpoint();
                try (WriteBatch batch = new WriteBatch(); WriteOptions options = new WriteOptions().setSync(true)) {
                    TablespaceRecord next = record.toBuilder().setSecondaryIndex(info).build();
                    tablespace.writeToBatch(table.yamcsInstance, batch, next.toBuilder());
                    batch.deleteRange(RdbStorageEngine.dbKey(getTbsIndex()), RdbStorageEngine.dbKey(getTbsIndex() + 1));
                    tablespace.getRdb().write(options, batch);
                    record = next;
                }
            } else {
                setInfo(info);
            }
        } finally {
            preparingBuild = false;
            table.indexLock.writeLock().unlock();
        }
    }

    private void backfill() throws Exception {
        // Partitions created after installation contain only live-maintained rows.
        for (var p : table.partitionManager.getPartitions()) {
            RdbPartition partition = (RdbPartition) p;
            SecondaryIndexCheckpoint checkpoint = getInfo().getCheckpointList().stream()
                    .filter(c -> c.getPartitionId() == partition.tbsIndex).findFirst()
                    .orElse(SecondaryIndexCheckpoint.newBuilder().setPartitionId(partition.tbsIndex).build());
            if (checkpoint.getComplete()) {
                continue;
            }
            YRDB db = tablespace.getRdb(partition.dir, false);
            try {
                long processed = getInfo().getProcessedRows();
                long missing = getInfo().getMissingRows();
                var cf = db.getColumnFamilyHandle(table.cfName());
                if (cf == null) {
                    saveCheckpoint(checkpoint.toBuilder().setComplete(true), processed, missing);
                    continue;
                }
                byte[] prefix = RdbStorageEngine.dbKey(partition.tbsIndex);
                byte[] start = checkpoint.hasLastKey() ? checkpoint.getLastKey().toByteArray() : prefix;
                try (ReadOptions options = new ReadOptions();
                        AscendingRangeIterator it = new AscendingRangeIterator(db.getDb().newIterator(cf, options), start, prefix)) {
                    int batchCount = 0;
                    while (it.isValid()) {
                        if (closed) {
                            throw new IllegalStateException("Index build stopped");
                        }
                        beforeBackfillRow.run();
                        byte[] rowKey = it.key();
                        if (checkpoint.hasLastKey() && Arrays.equals(rowKey, start)) {
                            it.next();
                            continue;
                        }
                        table.indexLock.readLock().lock();
                        db.lock(rowKey);
                        try {
                            replayRow(db, rowKey);
                            byte[] value = db.get(cf, rowKey);
                            if (value != null) {
                                byte[] key = indexKey(rowKey, value);
                                if (key != null) {
                                    tablespace.getRdb().put(key, pointer(partition));
                                }
                                processed++;
                                if (key == null) {
                                    missing++;
                                }
                            }
                            checkpoint = checkpoint.toBuilder().setLastKey(ByteString.copyFrom(rowKey)).build();
                            if (++batchCount == 1000) {
                                saveCheckpoint(checkpoint.toBuilder(), processed, missing);
                                batchCount = 0;
                            }
                        } finally {
                            db.unlock(rowKey);
                            table.indexLock.readLock().unlock();
                        }
                        it.next();
                    }
                    saveCheckpoint(checkpoint.toBuilder().setComplete(true), processed, missing);
                }
            } finally {
                tablespace.dispose(db);
            }
        }
        table.indexLock.writeLock().lock();
        try {
            recover();
            if (getInfo().getState() == State.FAILED) {
                throw new YarchException(getInfo().getFailure());
            }
            setInfo(getInfo().toBuilder().setState(State.READY).clearFailure());
        } finally {
            table.indexLock.writeLock().unlock();
        }
    }

    private synchronized void saveCheckpoint(SecondaryIndexCheckpoint.Builder checkpoint, long processed, long missing)
            throws RocksDBException {
        SecondaryIndex.Builder info = getInfo().toBuilder().setProcessedRows(processed).setMissingRows(missing);
        int position = -1;
        for (int i = 0; i < info.getCheckpointCount(); i++) {
            if (info.getCheckpoint(i).getPartitionId() == checkpoint.getPartitionId()) {
                position = i;
                break;
            }
        }
        if (position < 0) {
            info.addCheckpoint(checkpoint);
        } else {
            info.setCheckpoint(position, checkpoint);
        }
        setInfo(info);
    }

    void close() {
        closed = true;
        CompletableFuture<Void> runningBuild = build;
        if (runningBuild != null) {
            // Finish releasing partition references before the tablespace closes its native databases.
            runningBuild.handle((v, e) -> null).join();
        }
    }
}
