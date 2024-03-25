package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.ProtobufDatabase;
import org.yamcs.yarch.Sequence;
import org.yamcs.yarch.SequenceInfo;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Storage Engine based on RocksDB. Data is stored in multiple {@link Tablespace}.
 * <p>
 * Each table has associated a tablespace, if not set at creation of the table, it is the tablespace associated to the
 * instance.
 * 
 */
public class RdbStorageEngine implements StorageEngine {
    Map<String, Tablespace> tablespaces = new HashMap<>();
    Map<String, RdbBucketDatabase> bucketDbs = new HashMap<>();
    Map<String, RdbProtobufDatabase> protobufDbs = new HashMap<>();

    // number of bytes taken by the tbsIndex (prefix for all keys)
    public static final int TBS_INDEX_SIZE = 4;
    public static final byte[] ZERO_BYTES = new byte[0];

    static {
        RocksDB.loadLibrary();
    }
    static Log log = new Log(RdbStorageEngine.class);
    boolean ignoreVersionIncompatibility = false;
    static RdbStorageEngine instance = new RdbStorageEngine();

    RdbStorageEngine() {
    }

    void loadTablespaces(boolean readOnly) throws YarchException {
        File dir = new File(YarchDatabase.getDataDir());
        log.debug("Loading tablespaces from {}", dir);
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
    public void dropTable(YarchDatabaseInstance ydb, TableDefinition tbl) throws YarchException {
        Tablespace tablespace = getTablespace(ydb, tbl);

        log.info("Dropping table {}.{}", ydb.getName(), tbl.getName());
        try {
            tablespace.dropTable(tbl);
        } catch (RocksDBException | IOException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public RdbTableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tblDef, InsertMode insertMode) {
        checkFormatVersion(ydb, tblDef);
        Tablespace tablespace = getTablespace(ydb, tblDef);
        return tablespace.newTableWriter(ydb, tblDef, insertMode);
    }

    @Override
    public TableWalker newTableWalker(ExecutionContext ctx, TableDefinition tbl,
            boolean ascending, boolean follow) {
        Tablespace tblsp = getTablespace(ctx.getDb(), tbl);

        return tblsp.newTableWalker(ctx, tbl, ascending, follow);
    }

    @Override
    public void createTable(YarchDatabaseInstance ydb, TableDefinition def) throws YarchException {
        Tablespace tblsp = getTablespace(ydb, def);
        try {
            tblsp.createTable(ydb.getYamcsInstance(), def);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }

    }

    public static synchronized RdbStorageEngine getInstance() {
        return instance;
    }

    @Override
    public RdbPartitionManager getPartitionManager(YarchDatabaseInstance ydb, TableDefinition tblDef) {
        Tablespace tblsp = getTablespace(ydb, tblDef);
        return tblsp.getTable(tblDef).getPartitionManager();
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

    /**
     * Gets the tablespace storing the table data.
     * <p>
     * Creates the tablespace if it does not exist.
     * 
     * @param ydb
     * @param tbl
     * @return
     */
    private synchronized Tablespace getTablespace(YarchDatabaseInstance ydb, TableDefinition tbl) {
        return getTablespace(ydb);
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
            e.printStackTrace();
            log.error("Cannot load tablespace {}", tablespaceName, e);
            YamcsServer.getServer().getGlobalCrashHandler().handleCrash("RdbStorageEngine",
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

        if (tblDef.getFormatVersion() < 2) {
            throw new IllegalStateException("Table " + ydb.getName() + "/" + tblDef.getName() + " format version is "
                    + tblDef.getFormatVersion() + " instead of " + TableDefinition.CURRENT_FORMAT_VERSION
                    + ", please upgrade (use the \"yamcs archive upgrade\" command).");
        }
    }

    @Override
    public HistogramIterator getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef,
            String columnName, TimeInterval interval) throws YarchException {

        checkFormatVersion(ydb, tblDef);
        try {
            Tablespace tblsp = getTablespace(ydb);
            return new RdbHistogramIterator(ydb.getYamcsInstance(), tblsp, tblDef, columnName, interval);
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

    @Override
    public synchronized ProtobufDatabase getProtobufDatabase(YarchDatabaseInstance ydb) throws YarchException {
        String tablespaceName = ydb.getTablespaceName();
        String yamcsInstance = ydb.getYamcsInstance();
        RdbProtobufDatabase db = protobufDbs.get(tablespaceName);
        if (db == null) {
            try {
                db = new RdbProtobufDatabase(yamcsInstance, getTablespace(ydb));
            } catch (RocksDBException e) {
                throw new YarchException("Cannot create protobuf database", e);
            }
            protobufDbs.put(tablespaceName, db);
        }
        return db;
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
        tablespaces.clear();
        bucketDbs.clear();
        protobufDbs.clear();
    }

    @Override
    public List<TableDefinition> loadTables(YarchDatabaseInstance ydb) throws YarchException {
        Tablespace tablespace = getTablespace(ydb);
        try {
            return tablespace.loadTables(ydb.getYamcsInstance());
        } catch (RocksDBException | IOException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public void renameTable(YarchDatabaseInstance ydb, TableDefinition tblDef, String newName) {
        Tablespace tablespace = getTablespace(ydb);
        try {
            tablespace.renameTable(ydb.getYamcsInstance(), tblDef, newName);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public void migrateTableDefinition(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException {
        Tablespace tablespace = getTablespace(ydb);
        try {
            tablespace.migrateTableDefinition(ydb.getYamcsInstance(), tblDef);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public void saveTableDefinition(YarchDatabaseInstance ydb, TableDefinition tblDef,
            List<TableColumnDefinition> keyColumns,
            List<TableColumnDefinition> valueColumns) throws YarchException {
        Tablespace tablespace = getTablespace(ydb);
        try {
            tablespace.saveTableDefinition(ydb.getYamcsInstance(), tblDef, keyColumns, valueColumns);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public Sequence getSequence(YarchDatabaseInstance ydb, String name, boolean create) throws YarchException {
        try {
            return getTablespace(ydb).getSequence(name, create);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }

    @Override
    public TableWalker newSecondaryIndexTableWalker(YarchDatabaseInstance ydb, TableDefinition tableDefinition,
            boolean ascending, boolean follow) {
        Tablespace tblsp = getTablespace(ydb, tableDefinition);

        return tblsp.newSecondaryIndexTableWalker(ydb, tableDefinition, ascending, follow);
    }

    @Override
    public List<SequenceInfo> getSequencesInfo(YarchDatabaseInstance ydb) {
        return getTablespace(ydb).getSequencesInfo();
    }

    /**
     * returns a byte array containing the encoded tbsIndex
     */
    public static final byte[] dbKey(int tbsIndex) {
        return ByteArrayUtils.encodeInt(tbsIndex, new byte[TBS_INDEX_SIZE], 0);
    }

    /**
     * returns a byte array containing the encoded tbsIndex followed by the given key
     */
    static final byte[] dbKey(int tbsIndex, byte[] key) {
        byte[] dbKey = ByteArrayUtils.encodeInt(tbsIndex, new byte[key.length + 4], 0);
        System.arraycopy(key, 0, dbKey, 4, key.length);
        return dbKey;
    }

    static final int tbsIndex(byte[] dbKey) {
        return ByteArrayUtils.decodeInt(dbKey, 0);
    }

    /**
     * 
     * returns a system parameter producer for the databases used by the yamcsInstance
     * <p>
     * the producer will provide statistics with the memory usage
     */
    public RocksdbSysParamProducer newRdbParameterProducer(String yamcsInstance, SystemParametersService sps) {
        var tablespace = getTablespace(YarchDatabase.getInstance(yamcsInstance));

        return new RocksdbSysParamProducer(tablespace, sps);
    }

}
