package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.archive.TagDb;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableDefinition.PartitionStorage;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

/**
 * Storage Engine based on RocksDB.
 * 
 * Tables are mapped to multiple RocksDB databases - one for each time based partition.
 * Value based partitions are mapped to column families.
 * 
 * 
 */
public class RdbStorageEngine implements StorageEngine {
    Map<TableDefinition, RdbPartitionManager> partitionManagers = new HashMap<>();
    final YarchDatabase ydb;
    static Map<YarchDatabase, RdbStorageEngine> instances = new HashMap<>();
    static {
        RocksDB.loadLibrary();
    }
    static Logger log = LoggerFactory.getLogger(RdbStorageEngine.class.getName());
    RdbTagDb rdbTagDb = null;
    boolean ignoreVersionIncompatibility = false;

    public RdbStorageEngine(YarchDatabase ydb, boolean ignoreVersionIncompatibility) throws YarchException {
        this.ydb = ydb;
        instances.put(ydb, this);
        this.ignoreVersionIncompatibility = ignoreVersionIncompatibility;
    }

    public RdbStorageEngine(YarchDatabase ydb) throws YarchException {
        this(ydb, false);
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
    public TableWriter newTableWriter(TableDefinition tblDef, InsertMode insertMode) throws YarchException {
        if(!partitionManagers.containsKey(tblDef)) {
            throw new IllegalArgumentException("Do not have a partition manager for this table");
        }
        checkFormatVersion(tblDef);

        try {
            if(tblDef.isPartitionedByValue()) {
                if(tblDef.getPartitionStorage()==PartitionStorage.COLUMN_FAMILY) {
                    return new CfTableWriter(ydb, tblDef, insertMode, partitionManagers.get(tblDef));
                } else if(tblDef.getPartitionStorage()==PartitionStorage.IN_KEY) {
                    return new InKeyTableWriter(ydb, tblDef, insertMode, partitionManagers.get(tblDef));
                } else {
                    throw new IllegalArgumentException("Unknwon partition storage: "+tblDef.getPartitionStorage());
                }
            } else {
                return new CfTableWriter(ydb, tblDef, insertMode, partitionManagers.get(tblDef));
            }
        } catch (IOException e) {
            throw new YarchException("Failed to create writer", e);
        } 
    }


    @Override
    public AbstractStream newTableReaderStream(TableDefinition tbl, boolean ascending, boolean follow) {
        if(!partitionManagers.containsKey(tbl)) {
            throw new IllegalArgumentException("Do not have a partition manager for this table");
        }
        if(tbl.isPartitionedByValue()) {
            if(tbl.getPartitionStorage()==PartitionStorage.COLUMN_FAMILY) {
                return new CfTableReaderStream(ydb, tbl, partitionManagers.get(tbl), ascending, follow);
            } else if(tbl.getPartitionStorage()==PartitionStorage.IN_KEY) {
                return new InkeyTableReaderStream(ydb, tbl, partitionManagers.get(tbl), ascending, follow);
            } else {
                throw new RuntimeException("Unknwon partition storage: "+tbl.getPartitionStorage());
            }
        } else {
            return new CfTableReaderStream(ydb, tbl, partitionManagers.get(tbl), ascending, follow);
        }
    }

    @Override
    public void createTable(TableDefinition def) {		
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
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        rdbFactory.shutdown();
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

    /**
     * set to ignore version incompatibility - only used from the version upgrading functions to allow loading old tables.
     * 
     * @param b
     */
    public void setIgnoreVersionIncompatibility(boolean b) {
        this.ignoreVersionIncompatibility = b;
    }

    private void checkFormatVersion(TableDefinition tblDef) throws YarchException {
        if(ignoreVersionIncompatibility) {
            return;
        }

        if(tblDef.getFormatVersion()!=TableDefinition.CURRENT_FORMAT_VERSION) {
            throw new YarchException("Table "+ydb.getName()+"/"+tblDef.getName()+" format version is "+tblDef.getFormatVersion()
            + " instead of "+TableDefinition.CURRENT_FORMAT_VERSION+", please upgrade (use the \"yamcs archive upgrade\" command).");
        }
    }
    @Override
    public Iterator<HistogramRecord> getHistogramIterator(TableDefinition tblDef, String columnName, TimeInterval interval, long mergeTime) throws YarchException {
        checkFormatVersion(tblDef);
        try {
            return new RdbHistogramIterator(ydb, tblDef, columnName, interval, mergeTime);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }
}
