package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rocksdb.ColumnFamilyHandle;

import org.rocksdb.RocksDBException;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.yarch.Table;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

/**
 * Holds together the RDB properties related to one table.
 * 
 * @author nm
 *
 */
public class RdbTable extends Table {
    final int tbsIndex;
    final Tablespace tablespace;
    final String yamcsInstance;
    final RdbPartitionManager partitionManager;
    final HistogramWriter histoWriter;
    // column family name
    final String cfName;
    volatile SecondaryIndexWriter indexWriter;
    final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock(true);

    public RdbTable(String yamcsInstance, Tablespace tablespace, TableDefinition tblDef, int tbsIndex, String cfName) {
        super(tblDef);
        this.tbsIndex = tbsIndex;
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;
        this.cfName = cfName;

        partitionManager = new RdbPartitionManager(this, yamcsInstance, tblDef);

        histoWriter = HistogramWriter.newWriter(this);
        if (tblDef.hasSecondaryIndex()) {
            List<TablespaceRecord> trList = tablespace.filter(Type.SECONDARY_INDEX, yamcsInstance,
                    tr -> tr.getTableName().equals(tblDef.getName()));
            if(trList.size()!=1) {
                throw new DatabaseCorruptionException("Expected to read 1 secondary index record, got "+trList.size());
            }
            
            indexWriter = new SecondaryIndexWriter(this, trList.get(0));
        }
    }

    public Tablespace getTablespace() {
        return tablespace;
    }

    // called at instance start to read the table partitions
    void readPartitions() {
        try {
            partitionManager.readPartitions();
            if (indexWriter != null) {
                indexWriter.recover();
            }
        } catch (RocksDBException | IOException e) {
            throw new YarchException(e);
        }
    }

    public RdbPartitionManager getPartitionManager() {
        return partitionManager;
    }

    public HistogramWriter getHistogramWriter() {
        return histoWriter;
    }

    public SecondaryIndexWriter getSecondaryIndexWriter() {
        return indexWriter;
    }

    RdbPartition findPartition(int id) {
        return partitionManager.getPartitions().stream().map(p -> (RdbPartition) p)
                .filter(p -> p.tbsIndex == id).findFirst().orElse(null);
    }

    void executeAction(YRDB db, ColumnFamilyHandle cf, RdbPartition partition, byte[] rowKey,
            TableVisitor.Action action) throws RocksDBException, StreamSqlException {
        if (action.action() == TableVisitor.ActionType.NONE) {
            return;
        }
        indexLock.readLock().lock();
        db.lock(rowKey);
        try {
            if (indexWriter == null) {
                RdbTableWalker.executeAction(db, cf, action, rowKey);
                return;
            }
            byte[] newKey = rowKey;
            if (action.action() == TableVisitor.ActionType.UPDATE_ROW) {
                newKey = RdbStorageEngine.dbKey(partition.tbsIndex, action.getUpdatedKey());
                if (db.get(cf, newKey) != null) {
                    throw new StreamSqlException(ErrCode.DUPLICATE_KEY, "duplicate key in update");
                }
            }
            byte[] value = action.action() == TableVisitor.ActionType.DELETE ? null : action.getUpdatedValue();
            indexWriter.write(db, cf, partition, rowKey, newKey, value);
        } finally {
            db.unlock(rowKey);
            indexLock.readLock().unlock();
        }
    }

    public RdbHistogramInfo createAndGetHistogram(long instant, String columnName) {
        return (RdbHistogramInfo) partitionManager.createAndGetHistogram(instant, columnName);
    }

    public String cfName() {
        return cfName;
    }

}
