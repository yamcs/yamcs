package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.archive.TagDb;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

/**
 * Storage Engine based on RocksDB. Data is stored in multiple {@link Tablespace}
 * 
 */
public class RdbStorageEngine implements StorageEngine {
    Map<TableDefinition, RdbPartitionManager> partitionManagers = new HashMap<>();
    Map<String, Tablespace> tablespaces = new HashMap<>();
    Map<String, RdbTagDb> tagDbs = new HashMap<>();
    Map<String, RdbBucketDatabase> bucketDbs = new HashMap<>();

    // number of bytes taken by the tbsIndex (prefix for all keys)
    public static final int TBS_INDEX_SIZE = 4;

    static {
        RocksDB.loadLibrary();
    }
    static Logger log = LoggerFactory.getLogger(RdbStorageEngine.class.getName());
    RdbTagDb rdbTagDb = null;
    boolean ignoreVersionIncompatibility = false;
    static RdbStorageEngine instance = new RdbStorageEngine();

    RdbStorageEngine() {
    }

    public void loadTablespaces(boolean readOnly) throws YarchException {
        File dir = new File(YarchDatabase.getDataDir());
        if (dir.exists()) {
            File[] dirFiles = dir.listFiles();
            if (dirFiles == null) {
                return; // no tables found
            }
            for (File f : dirFiles) {
                String fn = f.getName();
                if (fn.endsWith(".rdb")) {
                    try {
                        String name = fn.substring(0, fn.length() - 4);
                        Tablespace tablespace = new Tablespace(name);
                        tablespace.loadDb(readOnly);
                        tablespaces.put(tablespace.getName(), tablespace);
                    } catch (IOException e) {
                        log.warn("Got exception when reading the table definition from {}: ", f, e);
                        throw new YarchException("Got exception when reading the table definition from " + f + ": ", e);
                    }
                }
            }
        }
    }

    @Override
    public void loadTable(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException {
        Tablespace tablespace = getTablespace(ydb, tblDef);
        RdbPartitionManager pm = new RdbPartitionManager(tablespace, ydb, tblDef);
        partitionManagers.put(tblDef, pm);
        try {
            pm.readPartitions();
        } catch (RocksDBException | IOException e) {
            log.error("Got exception when loading table partitions for {}.{}: ", ydb.getName(), tblDef.getName(), e);
            throw new YarchException("Got exception when reading table partitions", e);
        }
    }

    @Override
    public void dropTable(YarchDatabaseInstance ydb, TableDefinition tbl) throws YarchException {
        RdbPartitionManager pm = partitionManagers.remove(tbl);
        Tablespace tablespace = getTablespace(ydb, tbl);

        log.info("Dropping table {}.{}", ydb.getName(), tbl.getName());
        for (Partition p : pm.getPartitions()) {
            RdbPartition rdbp = (RdbPartition) p;

            int tbsIndex = rdbp.tbsIndex;
            log.debug("Removing tbsIndex {}", tbsIndex);
            try {
                YRDB db = tablespace.getRdb(rdbp.dir, false);
                db.getDb().deleteRange(dbKey(tbsIndex), dbKey(tbsIndex + 1));
                tablespace.removeTbsIndex(Type.TABLE_PARTITION, tbsIndex);
            } catch (IOException | RocksDBException e) {
                log.error("Error when removing tbsIndex", e);
                throw new YarchException(e);
            }
        }
    }

    @Override
    public TableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tblDef, InsertMode insertMode) {
        if (!partitionManagers.containsKey(tblDef)) {
            throw new IllegalStateException("Do not have a partition manager for this table");
        }
        checkFormatVersion(ydb, tblDef);

        return new RdbTableWriter(getTablespace(ydb, tblDef), ydb, tblDef, insertMode, partitionManagers.get(tblDef));
    }

    @Override
    public Stream newTableReaderStream(YarchDatabaseInstance ydb, TableDefinition tbl, boolean ascending,
            boolean follow) {
        if (!partitionManagers.containsKey(tbl)) {
            throw new IllegalStateException("Do not have a partition manager for this table");
        }

        return new RdbTableReaderStream(getTablespace(ydb, tbl), ydb, tbl, partitionManagers.get(tbl), ascending,
                follow);
    }

    @Override
    public void createTable(YarchDatabaseInstance ydb, TableDefinition def) {
        RdbPartitionManager pm = new RdbPartitionManager(getTablespace(ydb, def), ydb, def);
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
        RdbTagDb rdbTagDb = tagDbs.get(ydb.getName());
        if (rdbTagDb == null) {
            try {
                rdbTagDb = new RdbTagDb(ydb.getName(), getTablespace(ydb));
                tagDbs.put(ydb.getName(), rdbTagDb);
            } catch (RocksDBException e) {
                throw new YarchException("Cannot create tag db", e);
            }
        }
        return rdbTagDb;
    }

    /**
     * Create and/or get the tablespace for the yarch database instance.
     * 
     * @param ydb
     * @return
     */
    public synchronized Tablespace getTablespace(YarchDatabaseInstance ydb) {
        String tablespaceName = ydb.getTablespaceName();
        if (tablespaces.containsKey(tablespaceName)) {
            return tablespaces.get(tablespaceName);
        } else {
            createTablespace(tablespaceName);
        }
        return tablespaces.get(tablespaceName);

    }

    private synchronized Tablespace getTablespace(YarchDatabaseInstance ydb, TableDefinition tbl) {
        String tablespaceName = tbl.getTablespaceName();
        if (tablespaceName == null) {
            return getTablespace(ydb);
        } else {
            if (!tablespaces.containsKey(tablespaceName)) {
                createTablespace(tablespaceName);
            }
            return tablespaces.get(tablespaceName);
        }
    }

    public Map<String, Tablespace> getTablespaces() {
        return tablespaces;
    }

    public synchronized Tablespace createTablespace(String tablespaceName) {
        log.info("Creating or loading tablespace {}", tablespaceName);
        Tablespace t = new Tablespace(tablespaceName);
        try {
            t.loadDb(false);
        } catch (IOException e) {
            log.error("Got exception when creating or loading tablespapce ", e);
            YamcsServer.getGlobalCrashHandler().handleCrash("RdbStorageEngine",
                    "Error creating or loading tablespace:" + e);
            throw new UncheckedIOException(e);
        }
        tablespaces.put(tablespaceName, t);
        return t;
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

    private void checkFormatVersion(YarchDatabaseInstance ydb, TableDefinition tblDef) {
        if (ignoreVersionIncompatibility) {
            return;
        }

        if (tblDef.getFormatVersion() != TableDefinition.CURRENT_FORMAT_VERSION) {
            throw new IllegalStateException("Table " + ydb.getName() + "/" + tblDef.getName() + " format version is "
                    + tblDef.getFormatVersion() + " instead of " + TableDefinition.CURRENT_FORMAT_VERSION
                    + ", please upgrade (use the \"yamcs archive upgrade\" command).");
        }
    }

    @Override
    public HistogramIterator getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef,
            String columnName, TimeInterval interval, long mergeTime) throws YarchException {

        checkFormatVersion(ydb, tblDef);
        try {
            return new RdbHistogramIterator(getTablespace(ydb, tblDef), ydb, tblDef, columnName, interval, mergeTime);
        } catch (RocksDBException | IOException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public synchronized BucketDatabase getBucketDatabase(YarchDatabaseInstance ydb) throws YarchException {
        String tablespaceName = ydb.getTablespaceName();
        String yamcsInstance = ydb.getYamcsInstance();
        RdbBucketDatabase bdb = bucketDbs.get(tablespaceName);
        if (bdb == null) {
            try {
                bdb = new RdbBucketDatabase(yamcsInstance, getTablespace(ydb));
            } catch (RocksDBException | IOException e) {
                throw new YarchException("Cannot create bucket database", e);
            }
            bucketDbs.put(tablespaceName, bdb);
        }
        return bdb;
    }

    static byte[] dbKey(int tbsIndex) {
        return ByteArrayUtils.encodeInt(tbsIndex, new byte[TBS_INDEX_SIZE], 0);
    }

    static byte[] dbKey(int tbsIndex, byte[] key) {
        byte[] dbKey = ByteArrayUtils.encodeInt(tbsIndex, new byte[key.length + 4], 0);
        System.arraycopy(key, 0, dbKey, 4, key.length);
        return dbKey;
    }

    public Tablespace getTablespace(String tablespaceName) {
        return tablespaces.get(tablespaceName);
    }

    public void dropTablespace(String tablespaceName) {
        Tablespace tablespace = tablespaces.remove(tablespaceName);
        if (tablespace == null) {
            throw new IllegalArgumentException("No tablespace named '" + tablespaceName + "'");
        }
        tablespace.close();
    }

    public synchronized void shutdown() {
        for (Tablespace t : tablespaces.values()) {
            t.close();
        }
        partitionManagers.clear();
    }
}
