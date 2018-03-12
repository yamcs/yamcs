package org.yamcs.yarch.rocksdb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.archive.TagDb;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.rocksdb.RdbHistogramIterator;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import org.yaml.snakeyaml.Yaml;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Storage Engine based on RocksDB. Data is stored in multiple {@link Tablespace}
 * 
 */
public class RdbStorageEngine implements StorageEngine {
    Map<TableDefinition, RdbPartitionManager> partitionManagers = new HashMap<>();
    Map<String, Tablespace> tablespaces = new HashMap<>();
    Map<String, RdbTagDb> tagDbs = new HashMap<>();
    
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
                if (fn.endsWith(".tbs")) {
                    try {
                        Tablespace tablespace = deserializeTablespace(f);
                        tablespace.loadDb(readOnly);
                        tablespaces.put(tablespace.getName(), tablespace);
                    } catch (IOException | RocksDBException e) {
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
    public AbstractStream newTableReaderStream(YarchDatabaseInstance ydb, TableDefinition tbl, boolean ascending,
            boolean follow) {
        if (!partitionManagers.containsKey(tbl)) {
            throw new IllegalStateException("Do not have a partition manager for this table");
        }

        return new RdbTableReaderStream(getTablespace(ydb, tbl), ydb, tbl, partitionManagers.get(tbl), ascending, follow);
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
        if(rdbTagDb==null) {
            try {
                rdbTagDb = new RdbTagDb(ydb.getName(), getTablespace(ydb.getTablespaceName()));
                tagDbs.put(ydb.getName(), rdbTagDb);
            } catch (RocksDBException e) {
                throw new YarchException("Cannot create tag db",e);
            }
        }
        return rdbTagDb;
    }

    private synchronized Tablespace getTablespace(YarchDatabaseInstance ydb, TableDefinition tbl) {
        String tablespaceName = tbl.getTablespaceName();
        if (tablespaceName == null) {
            tablespaceName = ydb.getTablespaceName();
        }
        if (tablespaces.containsKey(tablespaceName)) {
            return tablespaces.get(tablespaceName);
        } else {
            createTablespace(tablespaceName);
        }
        return tablespaces.get(tablespaceName);
    }
    
    public Map<String, Tablespace> getTablespaces() {
        return tablespaces;
    }

    public synchronized Tablespace createTablespace(String tablespaceName) {
        log.info("Creating tablespace {}", tablespaceName);
        int id = tablespaces.values().stream().mapToInt(t -> t.getId()).max().orElse(-1);
        id = (id+1) & 0x7F; //make sure the first bit is always 0
        Tablespace t = new Tablespace(tablespaceName, (byte) id);

        String fn = YarchDatabase.getDataDir() + "/" + tablespaceName + ".tbs";
        try (FileOutputStream fos = new FileOutputStream(fn)) {
            Yaml yaml = new Yaml(new TablespaceRepresenter());
            Writer w = new BufferedWriter(new OutputStreamWriter(fos));
            yaml.dump(t, w);
            w.flush();
            fos.getFD().sync();
            w.close();
            t.loadDb(false);
        } catch (IOException | RocksDBException e) {
            log.error("Got exception when writing tablespapce definition to {} ", fn, e);
            YamcsServer.getGlobalCrashHandler().handleCrash("RdbStorageEngine",
                    "Cannot write tablespace definition to " + fn + " :" + e);
            throw new RuntimeException(e);
        }

        tablespaces.put(tablespaceName, t);
        return t;
    }

    private Tablespace deserializeTablespace(File f) throws IOException {
        String fn = f.getName();

        try (FileInputStream fis = new FileInputStream(f)) {
            String tablespaceName = fn.substring(0, fn.length() - 4);
            Yaml yaml = new Yaml(new TablespaceConstructor(tablespaceName));
            Object o = yaml.load(fis);
            if (!(o instanceof Tablespace)) {
                fis.close();
                throw new IOException("Cannot load tablespace definition from " + f + ": object is "
                        + o.getClass().getName() + "; should be " + Tablespace.class.getName());
            }
            Tablespace tablespace = (Tablespace) o;
            fis.close();
            return tablespace;
        }
    }

    /**
     * set to ignore version incompatibility - only used from the version
     * upgrading functions to allow loading old tables.
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
        for(Tablespace t: tablespaces.values()) {
            t.close();
        }
        partitionManagers.clear();
    }
}
