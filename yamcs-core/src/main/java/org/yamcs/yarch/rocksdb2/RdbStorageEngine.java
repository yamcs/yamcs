package org.yamcs.yarch.rocksdb2;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.archive.TagDb;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramDb;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.RdbHistogramDb;

/**
 * Storage Engine based on RocksDB.
 * 
 * Tables are mapped to multiple RocksDB databases - one for each time based partition.
 * Different from rocksdb , the value based partitions are encoded in front of the keys. 
 * 
 * 
 */
public class RdbStorageEngine implements StorageEngine {
    Map<TableDefinition, RdbPartitionManager> partitionManagers = new HashMap<TableDefinition, RdbPartitionManager>();
    final YarchDatabase ydb;
    static Map<YarchDatabase, RdbStorageEngine> instances = new HashMap<YarchDatabase, RdbStorageEngine>();
    static {
        RocksDB.loadLibrary();
    }
    static Logger log=LoggerFactory.getLogger(RdbStorageEngine.class.getName());
    RdbTagDb rdbTagDb = null;

    public RdbStorageEngine(YarchDatabase ydb) throws YarchException {
        this.ydb = ydb;
        instances.put(ydb, this);
    }


    @Override
    public void loadTable(TableDefinition tbl) throws YarchException {
        if(tbl.hasPartitioning()) {
            RdbPartitionManager pm = new RdbPartitionManager(ydb, tbl);
            pm.readPartitionsFromDisk();
            partitionManagers.put(tbl, pm);
        }
    }

    @Override
    public void dropTable(TableDefinition tbl) throws YarchException {
        RdbPartitionManager pm = partitionManagers.remove(tbl);

        for(Partition p:pm.getPartitions()) {
            RdbPartition rdbp = (RdbPartition)p;
            File f=new File(tbl.getDataDir()+"/"+rdbp.dir);
            RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
            rdbFactory.closeIfOpen(f.getAbsolutePath());
            try {
                if(f.exists()) {
                    log.debug("Recursively removing {}", f);
                    FileUtils.deleteRecursively(f.toPath());
                }
            } catch (IOException e) {
                throw new YarchException("Cannot remove "+f, e);
            }
        }

    }

    @Override
    public TableWriter newTableWriter(TableDefinition tbl, InsertMode insertMode) throws YarchException {
        if(!partitionManagers.containsKey(tbl)) {
            throw new IllegalArgumentException("Do not have a partition manager for this table");
        }
        
        try {
            return new RdbTableWriter(ydb, tbl, insertMode, partitionManagers.get(tbl));
        } catch (IOException e) {
            throw new YarchException("Failed to create writer", e);
        } 
    }

    @Override
    public AbstractStream newTableReaderStream(TableDefinition tbl, boolean ascending, boolean follow) {
        if(!partitionManagers.containsKey(tbl)) {
            throw new IllegalArgumentException("Do not have a partition manager for this table");
        }
        return new RdbTableReaderStream(ydb, tbl, partitionManagers.get(tbl), ascending, follow);
    }
 
    @Override
    public void createTable(TableDefinition def) throws YarchException {
        PartitioningSpec pspec = def.getPartitioningSpec();
        if(pspec.type==_type.TIME_AND_VALUE|| pspec.type==_type.VALUE) {
            //allow partitioning by value only of fixed size data types
            DataType dt = pspec.getValueColumnType();
            
            if(ColumnValueSerializer.getSerializedSize(dt)==-1) {
                throw new YarchException("DataType "+dt+" not supported as partitioning column. Use one of the fixed size data types");
            }
        }
        RdbPartitionManager pm = new RdbPartitionManager(ydb, def);
        partitionManagers.put(def, pm);
    }

    public static synchronized RdbStorageEngine getInstance(YarchDatabase ydb) {
        return instances.get(ydb);
    }

    public RdbPartitionManager getPartitionManager(TableDefinition tdef) {      
        return partitionManagers.get(tdef);
    }


    @Override
    public HistogramDb getHistogramDb(TableDefinition tbl) throws YarchException {
        try {
            return RdbHistogramDb.getInstance(ydb, tbl);
        } catch (IOException e) {
            throw new YarchException(e);
        }
    }


    @Override
    public synchronized TagDb getTagDb() throws YarchException {
        if(rdbTagDb==null) {
            try {
                rdbTagDb = new RdbTagDb(ydb);
            } catch (RocksDBException e) {
                throw new YarchException("Cannot create tag db",e);
            }
        }
        return rdbTagDb;
    }

    /** 
     * Called from Unit tests to cleanup before the next test
     */
    public void shutdown() {
        if(rdbTagDb!=null) {
            rdbTagDb.close();
        }
    }

    /**
     * Called from unit tests to cleanup before the next test
     * @param ydb
     */
    public static synchronized void removeInstance(YarchDatabase ydb) {
        RdbStorageEngine rse = instances.remove(ydb);
        if(rse!=null) {
            rse.shutdown();
        }
    }
}
