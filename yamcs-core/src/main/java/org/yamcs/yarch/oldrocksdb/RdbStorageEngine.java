package org.yamcs.yarch.oldrocksdb;

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
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableDefinition.PartitionStorage;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Storage Engine based on RocksDB.
 * 
 * Tables are mapped to multiple RocksDB databases - one for each time based partition.
 * 
 * 
 */
@Deprecated
public class RdbStorageEngine implements StorageEngine {
    Map<TableDefinition, RdbPartitionManager> partitionManagers = new HashMap<>();
    Map<String, RdbTagDb> tagDbs = new HashMap<>();
    static {
        RocksDB.loadLibrary();
    }
    static Logger log = LoggerFactory.getLogger(RdbStorageEngine.class.getName());
    boolean ignoreVersionIncompatibility = false;
    static final RdbStorageEngine instance = new RdbStorageEngine();

    @Override
    public void loadTable(YarchDatabaseInstance ydb, TableDefinition tbl) throws YarchException {
        if (!ignoreVersionIncompatibility) {
            log.warn(
                    "You are using the old rocksdb storage engine for table {}. This is deprecated and it will be removed from future versions. "
                            + "Please upgrade using \"yamcs archive upgrade --instance " + ydb.getYamcsInstance()
                            + "\" command",
                    tbl.getName());
        }
        if (tbl.hasPartitioning()) {
            RdbPartitionManager pm = new RdbPartitionManager(ydb, tbl);
            pm.readPartitionsFromDisk();
            partitionManagers.put(tbl, pm);
        }
    }

    @Override
    public void dropTable(YarchDatabaseInstance ydb, TableDefinition tbl) throws YarchException {
        RdbPartitionManager pm = partitionManagers.remove(tbl);

        for (Partition p : pm.getPartitions()) {
            RdbPartition rdbp = (RdbPartition) p;
            File f = new File(tbl.getDataDir() + "/" + rdbp.dir);
            RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
            rdbFactory.closeIfOpen(f.getAbsolutePath());
            try {
                if (f.exists()) {
                    log.debug("Recursively removing {}", f);
                    FileUtils.deleteRecursively(f.toPath());
                }
            } catch (IOException e) {
                throw new YarchException("Cannot remove " + f, e);
            }
        }

    }

    @Override
    public TableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tblDef, InsertMode insertMode)
            throws YarchException {
        if (!partitionManagers.containsKey(tblDef)) {
            throw new IllegalArgumentException("Do not have a partition manager for this table");
        }
        checkFormatVersion(ydb, tblDef);

        try {
            if (tblDef.isPartitionedByValue()) {
                if (tblDef.getPartitionStorage() == PartitionStorage.COLUMN_FAMILY) {
                    return new CfTableWriter(ydb, tblDef, insertMode, partitionManagers.get(tblDef));
                } else if (tblDef.getPartitionStorage() == PartitionStorage.IN_KEY) {
                    return new InKeyTableWriter(ydb, tblDef, insertMode, partitionManagers.get(tblDef));
                } else {
                    throw new IllegalArgumentException("Unknwon partition storage: " + tblDef.getPartitionStorage());
                }
            } else {
                return new CfTableWriter(ydb, tblDef, insertMode, partitionManagers.get(tblDef));
            }
        } catch (IOException e) {
            throw new YarchException("Failed to create writer", e);
        }
    }

    @Override
    public Stream newTableReaderStream(YarchDatabaseInstance ydb, TableDefinition tbl, boolean ascending,
            boolean follow) {
        if (!partitionManagers.containsKey(tbl)) {
            throw new IllegalArgumentException("Do not have a partition manager for this table");
        }
        if (tbl.isPartitionedByValue()) {
            if (tbl.getPartitionStorage() == PartitionStorage.COLUMN_FAMILY) {
                return new CfTableReaderStream(ydb, tbl, partitionManagers.get(tbl), ascending, follow);
            } else if (tbl.getPartitionStorage() == PartitionStorage.IN_KEY) {
                return new InkeyTableReaderStream(ydb, tbl, partitionManagers.get(tbl), ascending, follow);
            } else {
                throw new RuntimeException("Unknwon partition storage: " + tbl.getPartitionStorage());
            }
        } else {
            return new CfTableReaderStream(ydb, tbl, partitionManagers.get(tbl), ascending, follow);
        }
    }

    @Override
    public void createTable(YarchDatabaseInstance ydb, TableDefinition def) {
        RdbPartitionManager pm = new RdbPartitionManager(ydb, def);
        partitionManagers.put(def, pm);
    }

    public static synchronized RdbStorageEngine getInstance() {
        return instance;
    }

    public RdbPartitionManager getPartitionManager(TableDefinition tdef) {
        return partitionManagers.get(tdef);
    }

    @Override
    public synchronized TagDb getTagDb(YarchDatabaseInstance ydb) throws YarchException {
        if (!ignoreVersionIncompatibility) {
            log.warn(
                    "You are using the old rocksdb storage engine for the tag database. This is deprecated and it will be removed from future versions. "
                            + "Please upgrade using \"yamcs archive upgrade --instance {}\" command",
                    ydb.getYamcsInstance());
        }
        RdbTagDb rdbTagDb = tagDbs.get(ydb.getName());
        if (rdbTagDb == null) {
            try {
                rdbTagDb = new RdbTagDb(ydb);
                tagDbs.put(ydb.getName(), rdbTagDb);
            } catch (RocksDBException e) {
                throw new YarchException("Cannot create tag db", e);
            }
        }
        return rdbTagDb;
    }

    /**
     * Called from Unit tests to cleanup before the next test
     */
    public void shutdown() {
        for (RDBFactory r : RDBFactory.instances.values()) {
            r.shutdown();
        }
    }

    /**
     * set to ignore version incompatibility - only used from the version upgrading functions to allow loading old
     * tables.
     * 
     * @param b
     */
    public void setIgnoreVersionIncompatibility(boolean b) {
        this.ignoreVersionIncompatibility = b;
    }

    private void checkFormatVersion(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException {
        if (ignoreVersionIncompatibility) {
            return;
        }

        if (tblDef.getFormatVersion() != TableDefinition.CURRENT_FORMAT_VERSION) {
            throw new YarchException("Table " + ydb.getName() + "/" + tblDef.getName() + " format version is "
                    + tblDef.getFormatVersion()
                    + " instead of " + TableDefinition.CURRENT_FORMAT_VERSION
                    + ", please upgrade (use the \"yamcs archive upgrade\" command).");
        }
    }

    @Override
    public HistogramIterator getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef, String columnName,
            TimeInterval interval, long mergeTime) throws YarchException {
        checkFormatVersion(ydb, tblDef);
        try {
            return new RdbHistogramIterator(ydb, tblDef, columnName, interval, mergeTime);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public BucketDatabase getBucketDatabase(YarchDatabaseInstance yarchDatabaseInstance) throws YarchException {
        return null;
    }
}
