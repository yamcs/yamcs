package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.List;

import org.rocksdb.RocksDBException;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.yarch.Table;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchException;
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
    SecondaryIndexWriter indexWriter;

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
            
            indexWriter = new SecondaryIndexWriter(tablespace, tblDef, trList.get(0).getTbsIndex());
        }
    }

    public Tablespace getTablespace() {
        return tablespace;
    }

    // called at instance start to read the table partitions
    void readPartitions() {
        try {
            partitionManager.readPartitions();
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

    public RdbHistogramInfo createAndGetHistogram(long instant, String columnName) {
        return (RdbHistogramInfo) partitionManager.createAndGetHistogram(instant, columnName);
    }

    public String cfName() {
        return cfName;
    }

}
