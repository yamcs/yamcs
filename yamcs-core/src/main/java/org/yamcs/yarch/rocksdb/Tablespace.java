package org.yamcs.yarch.rocksdb;

import static org.yamcs.utils.ByteArrayUtils.decodeInt;
import static org.yamcs.utils.ByteArrayUtils.encodeInt;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.MemoryUsageType;
import org.rocksdb.MemoryUtil;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.yamcs.alarms.EventAlarmStreamer;
import org.yamcs.alarms.ParameterAlarmStreamer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.logging.Log;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.IntArray;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.Sequence;
import org.yamcs.yarch.SequenceInfo;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.protobuf.Db;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ProtoTableDefinition;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndex;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;

/**
 * Tablespaces are used to store data by the {@link RdbStorageEngine}. Each tablespace can store data from one or more
 * Yamcs instances.
 * <p>
 * Tablespaces are rocksdb databases normally stored in the yamcs data directory (/storage/yamcs-data). Each corresponds
 * to a directory &lt;tablespace-name&gt;.rdb and has a definition file tablespace-name.tbs.
 * <p>
 * Tablespaces can also have time based partitions in different RocksDB databases in sub-directories such as
 * &lt;tablespace-name&gt;.rdb/YYYY/
 * <p>
 * There are four column families in the main database:
 * <ul>
 * <li>_metadata_ - contains metadata - tables definition, tbsIndices,...</li>
 * <li>rt_data - stores data for tm, pp and events tables.</li>
 * <li>parameter_archive - stores data for parameter archive</li>
 * <li>the default column family - contains other data (alarms, activities, timeline...)</li>
 * </ul>
 * <p>
 * The data is partitioned by the first 4 bytes of the key which we call tbsIndex.
 * <p>
 * One tbsIndex corresponds to a so called tablespace record. For example tbsIndex=5 can correspond to all telemetry
 * packets for packet XYZ.
 * <p>
 * Except for the 4 bytes tbsIndex, the rest of the key and value are completely dependent on the data type. For example
 * for yarch table data, the rest of key following the 4 bytes tbsIndex represents the key of the row in the table.
 * <p>
 * The metadata contains three types of records, identified by the first byte of the key:
 * <ul>
 * <li>key: 0x01
 * <p>
 * value: 1 byte version number (0x1), 4 bytes max tbsIndex
 * <p>
 * used to store the max tbsIndex and also stores a version number in case the format will change in the future
 * <li>key: 0x02, 1 byte record type, 4 bytes tbsIndex
 * <p>
 * value: protobuf encoded TablespaceRecord
 * <p>
 * Used to store the information corresponding to the given tbsIndex. The record type corresponds to the Type
 * enumerations from tablespace.proto</li>
 * <li>key: 0x03, 1 byte record type, sequence name encoded in UTF8
 * <p>
 * value: last sequence number 8 bytes big endian
 * </ul>
 */
public class Tablespace {
    private Log log;

    // unique name for this tablespace
    private final String name;

    private String customDataDir;
    public static final String CF_METADATA = "_metadata_";
    private static final byte PREV_METADATA_VERSION = 1;
    private static final byte METADATA_VERSION = 2;

    private static final byte[] METADATA_KEY_MAX_TBS_VERSION = new byte[] { 1 };
    static final byte METADATA_FB_TR = 2; // first byte of metadata records
                                          // keys that contain tablespace
                                          // records

    static final byte METADATA_FB_SEQ = 3;// first byte of metadata records keys that contain sequences

    YRDB mainDb;
    ColumnFamilyHandle cfMetadata;
    long maxTbsIndex;

    RDBFactory rdbFactory;

    Map<TableDefinition, RdbTable> tables = new HashMap<>();

    static final Object DUMMY = new Object();

    Map<TableWalker, Object> walkers = Collections.synchronizedMap(new WeakHashMap<TableWalker, Object>());
    final ScheduledThreadPoolExecutor executor;

    Map<TableDefinition, List<RdbTableWriter>> tableWriters = new HashMap<>();

    Map<String, RdbSequence> sequences = new HashMap<>();

    public Tablespace(String name) {
        log = new Log(Tablespace.class);
        log.setContext(name);
        this.name = name;
        this.executor = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat("Tablespace-" + name).build());
    }

    public void loadDb(boolean readonly) throws IOException {
        String dbDir = getDataDir();
        rdbFactory = new RDBFactory(dbDir, executor);
        File f = new File(dbDir, "CURRENT");
        try {
            if (f.exists()) {
                log.debug("Opening existing database {}", dbDir);
                mainDb = rdbFactory.getRdb(readonly);
                cfMetadata = mainDb.getColumnFamilyHandle(CF_METADATA);
                if (cfMetadata == null) {
                    throw new IOException("Existing tablespace database '" + dbDir
                            + "' does not contain a column family named '" + CF_METADATA);
                }

                byte[] value = mainDb.get(cfMetadata, METADATA_KEY_MAX_TBS_VERSION);
                if (value == null) {
                    throw new DatabaseCorruptionException(
                            "No (version, maxTbsIndex) record found in the metadata");
                }
                if (value[0] == PREV_METADATA_VERSION) {
                    updateMetadataVersion();
                } else if (value[0] != METADATA_VERSION) {
                    throw new DatabaseCorruptionException(
                            "Wrong metadata version " + value[0] + " expected " + METADATA_VERSION);
                }
                maxTbsIndex = Integer.toUnsignedLong(decodeInt(value, 1));
                log.info("Opened tablespace database {}", dbDir);
                log.info("Records: ~{}, metadata records: ~{}, maxTbsIndex: {}",
                        mainDb.getApproxNumRecords(), mainDb.getApproxNumRecords(cfMetadata), maxTbsIndex);
            } else {
                if (readonly) {
                    throw new IllegalStateException("Cannot create a new db when readonly is set to true");
                }
                log.info("Creating database at {}", dbDir);
                mainDb = rdbFactory.getRdb(readonly);
                cfMetadata = mainDb.createColumnFamily(CF_METADATA);
                initMaxTbsIndex();
            }
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    // metadata version 1-> 2. the only change is that we moved the table definitions into the rocksdb.
    // we update the version in order for older servers to throw an error if encountering a new database (otherwise they
    // will create again the tables shadowing the old data)
    private void updateMetadataVersion() throws RocksDBException {
        writeRootMetadata();
    }

    public String getName() {
        return name;
    }

    /**
     * Returns a list of all records of type TABLE_PARTITION for a given instance and table
     * 
     * If instanceName = tablespace name, it returns also records which do not have an instanceName specified.
     */
    public List<TablespaceRecord> getTablePartitions(String instanceName, String tableName)
            throws RocksDBException, IOException {
        return filter(Type.TABLE_PARTITION, instanceName, tr -> tableName.equals(tr.getTableName()));
    }

    /**
     * Returns a list of all records of type HISTOGRAM for a given instance and table
     * 
     * If instanceName = tablespace name, it returns also records which do not have an instanceName specified.
     */
    public List<TablespaceRecord> getTableHistograms(String instanceName, String tableName)
            throws RocksDBException, IOException {
        return filter(Type.HISTOGRAM, instanceName, tr -> tableName.equals(tr.getTableName()));
    }

    public List<TablespaceRecord> filter(Type type, String instanceName, Predicate<TablespaceRecord.Builder> p)
            throws YarchException, DatabaseCorruptionException {
        List<TablespaceRecord> rlist = new ArrayList<>();
        byte[] rangeStart = new byte[] { METADATA_FB_TR, (byte) type.getNumber() };

        try (AscendingRangeIterator arit = new AscendingRangeIterator(mainDb.newIterator(cfMetadata), rangeStart,
                rangeStart)) {
            while (arit.isValid()) {

                TablespaceRecord.Builder tr;
                try {
                    tr = TablespaceRecord.newBuilder().mergeFrom(arit.value());
                } catch (InvalidProtocolBufferException e) {
                    throw new DatabaseCorruptionException("Cannot decode tablespace record", e);
                }
                if (p.test(tr)) {
                    if (tr.hasInstanceName()) {
                        if (instanceName.equals(tr.getInstanceName())) {
                            rlist.add(tr.build());
                        }
                    } else {
                        if (instanceName.equals(name)) {
                            tr.setInstanceName(name);
                            rlist.add(tr.build());
                        }
                    }
                }
                arit.next();
            }
        } catch (RocksDBException e1) {
            throw new YarchException(e1);
        }
        return rlist;
    }

    /**
     * Creates a new tablespace record and adds it to the metadata database
     * 
     * @param trb
     *            the builder has to have all fields set except for the tbsIndex which will be assigned by this method
     * @return a fully built
     * @throws RocksDBException
     */
    public TablespaceRecord createMetadataRecord(String yamcsInstance, TablespaceRecord.Builder trb)
            throws RocksDBException {
        if (!trb.hasType()) {
            throw new IllegalArgumentException("The type is mandatory in the TablespaceRecord");
        }
        if (!name.equals(yamcsInstance)) {
            trb.setInstanceName(yamcsInstance);
        }
        int tbsIndex = getNextTbsIndex();
        trb.setTbsIndex(tbsIndex);

        TablespaceRecord tr = trb.build();
        log.debug("Adding new metadata record {}", tr);
        mainDb.put(cfMetadata, getMetadataKey(tr.getType(), tbsIndex), tr.toByteArray());
        return tr;
    }

    public TablespaceRecord updateRecord(String yamcsInstance, TablespaceRecord.Builder trb) throws RocksDBException {
        if (!trb.hasType()) {
            throw new IllegalArgumentException("The type is mandatory in the TablespaceRecord");
        }

        if (!trb.hasTbsIndex()) {
            throw new IllegalArgumentException("The tbsIndex is mandatory for update");
        }

        if (!name.equals(yamcsInstance)) {
            trb.setInstanceName(yamcsInstance);
        }

        TablespaceRecord tr = trb.build();
        log.debug("Updating metadata record {}", TextFormat.shortDebugString(tr));
        mainDb.put(cfMetadata, getMetadataKey(tr.getType(), tr.getTbsIndex()), tr.toByteArray());

        return tr;
    }

    TablespaceRecord writeToBatch(String yamcsInstance, WriteBatch writeBatch, TablespaceRecord.Builder trb)
            throws IOException {
        if (!trb.hasType()) {
            throw new IllegalArgumentException("The type is mandatory in the TablespaceRecord");
        }
        if (!trb.hasTbsIndex()) {
            throw new IllegalArgumentException("The tbsIndex is mandatory for update");
        }
        if (!name.equals(yamcsInstance)) {
            trb.setInstanceName(yamcsInstance);
        }

        TablespaceRecord tr = trb.build();
        log.debug("Updating metadata record {}", tr);
        try {
            writeBatch.put(cfMetadata, getMetadataKey(tr.getType(), tr.getTbsIndex()), tr.toByteArray());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        return tr;
    }

    public String getCustomDataDir() {
        return customDataDir;
    }

    private void initMaxTbsIndex() throws RocksDBException {
        maxTbsIndex = 0;
        getNextTbsIndex();
    }

    private synchronized int getNextTbsIndex() throws RocksDBException {
        maxTbsIndex++;
        writeRootMetadata();
        return (int) maxTbsIndex;
    }

    private void writeRootMetadata() throws RocksDBException {
        byte[] v = new byte[5];
        v[0] = METADATA_VERSION;
        encodeInt((int) maxTbsIndex, v, 1);
        mainDb.put(cfMetadata, METADATA_KEY_MAX_TBS_VERSION, v);
    }

    /**
     * (Creates) and returns a database in the given partition directory. If the directory is null, return then main
     * tablespace db
     * 
     * @param partitionDir
     * @param readOnly
     */
    public YRDB getRdb(String partitionDir, boolean readOnly) {
        if (partitionDir == null) {
            return mainDb;
        } else {
            try {
                return rdbFactory.getRdb(partitionDir, readOnly);
            } catch (IOException e) {
                throw new YarchException(e);
            }
        }
    }

    public YRDB getRdb(String relativePath) {
        return getRdb(relativePath, false);
    }

    /**
     * Get the main database of the tablespace
     */
    public YRDB getRdb() {
        return mainDb;
    }

    public void dispose(YRDB rdb) {
        if (mainDb == rdb) {
            return;
        } else {
            rdbFactory.dispose(rdb);
        }
    }

    public void setCustomDataDir(String dataDir) {
        this.customDataDir = dataDir;
    }

    public String getDataDir() {
        String dir = customDataDir;
        if (dir == null) {
            dir = YarchDatabase.getDataDir() + File.separator + name + ".rdb";
        }
        return dir;
    }

    /**
     * Removes the tbsIndex from the metadata and all the associated data from the main db (data might still be present
     * in the partitions)
     * 
     * @param type
     * @param tbsIndex
     * @throws RocksDBException
     */
    public void removeTbsIndex(Type type, int tbsIndex) throws RocksDBException {
        log.debug("Removing tbsIndex {}", tbsIndex);
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            wb.delete(cfMetadata, getMetadataKey(type, tbsIndex));
            byte[] beginKey = new byte[TBS_INDEX_SIZE];
            byte[] endKey = new byte[TBS_INDEX_SIZE];
            ByteArrayUtils.encodeInt(tbsIndex, beginKey, 0);
            ByteArrayUtils.encodeInt(tbsIndex + 1, endKey, 0);
            wb.deleteRange(beginKey, endKey);
            mainDb.getDb().write(wo, wb);
        }
    }

    /**
     * Removes the tbs indices with ALL the associated data from the main db (data might still be present in the
     * partitions)
     * 
     * @param type
     * @param tbsIndexArray
     * @throws RocksDBException
     */
    public void removeTbsIndices(Type type, IntArray tbsIndexArray) throws RocksDBException {
        log.debug("Removing tbsIndices {}", tbsIndexArray);
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            for (int i = 0; i < tbsIndexArray.size(); i++) {
                int tbsIndex = tbsIndexArray.get(i);
                wb.delete(cfMetadata, getMetadataKey(type, tbsIndexArray.get(i)));
                byte[] beginKey = dbKey(tbsIndex);
                byte[] endKey = dbKey(tbsIndex + 1);
                wb.deleteRange(beginKey, endKey);
            }
            mainDb.getDb().write(wo, wb);
        }
    }

    /**
     * Removes all metadata records of a given type
     * 
     * @param type
     * @throws RocksDBException
     */
    public void removeMetadataRecords(Type type) throws RocksDBException {
        byte[] beginKey = new byte[] { METADATA_FB_TR, (byte) type.getNumber() };
        byte[] endKey = new byte[] { METADATA_FB_TR, (byte) (type.getNumber() + 1) };
        mainDb.getDb().deleteRange(cfMetadata, beginKey, endKey);
    }

    /**
     * the key to use in the metadata table. We currently use 6 bytes: 1 byte fixed 0xFF 4 bytes tbsIndex
     * 
     * in the future we may use a byte different than 0xFF to store the same data sorted differently
     *
     * the reason for using 0xFF as the first byte is to get the highest tbsIndex by reading the last record.
     */
    private byte[] getMetadataKey(Type type, int tbsIndex) {
        byte[] key = new byte[6];
        key[0] = METADATA_FB_TR;
        key[1] = (byte) type.getNumber();
        encodeInt(tbsIndex, key, 2);
        return key;
    }

    public RDBFactory getRdbFactory() {
        return rdbFactory;
    }

    /**
     * inserts data into the main partition
     * 
     * @param key
     * @param value
     * @throws RocksDBException
     */
    public void putData(byte[] key, byte[] value) throws RocksDBException {
        checkKey(key);
        mainDb.put(key, value);
    }

    public byte[] getData(byte[] key) throws RocksDBException {
        return mainDb.get(key);
    }

    public void remove(byte[] key) throws RocksDBException {
        checkKey(key);
        mainDb.getDb().delete(key);
    }

    private void checkKey(byte[] key) {
        if (key.length < TBS_INDEX_SIZE) {
            throw new IllegalArgumentException("The key has to contain at least the tbsIndex");
        }
    }

    public void createTable(String yamcsInstance, TableDefinition tblDef) throws YarchException, RocksDBException {
        synchronized (tables) {
            ProtoTableDefinition rtd = TableDefinitionSerializer.toProtobuf(tblDef, tblDef.getKeyDefinition(),
                    tblDef.getValueDefinition());
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder();
            trb.setType(Type.TABLE_DEFINITION);
            trb.setTableDefinition(rtd);
            trb.setTableName(tblDef.getName());

            createMetadataRecord(yamcsInstance, trb);

            for (SecondaryIndex sidx : rtd.getSecondaryIndexList()) {
                TablespaceRecord.Builder trbsidx = TablespaceRecord.newBuilder();
                trbsidx.setType(Type.SECONDARY_INDEX);
                trbsidx.setSecondaryIndex(sidx);// only one secondary index supported for now
                trbsidx.setTableName(tblDef.getName());
                createMetadataRecord(yamcsInstance, trbsidx);
            }
            String cfName = tblDef.getCfName() == null ? YRDB.DEFAULT_CF : tblDef.getCfName();
            RdbTable table = new RdbTable(yamcsInstance, this, tblDef, trb.getTbsIndex(), cfName);

            tables.put(tblDef, table);

            configureAutoincrementSequences(yamcsInstance, tblDef);
        }
    }

    private void configureAutoincrementSequences(String yamcsInstance, TableDefinition tblDef)
            throws YarchException, RocksDBException {
        for (TableColumnDefinition tcd : tblDef.getKeyDefinition()) {
            if (tcd.isAutoIncrement()) {
                tcd.setSequence(autoincrementSequence(yamcsInstance, tblDef.getName(), tcd.getName()));
            }
        }
        for (TableColumnDefinition tcd : tblDef.getValueDefinition()) {
            if (tcd.isAutoIncrement()) {
                tcd.setSequence(autoincrementSequence(yamcsInstance, tblDef.getName(), tcd.getName()));
            }
        }
    }

    private Sequence autoincrementSequence(String yamcsInstance, String tableName, String columnName)
            throws YarchException, RocksDBException {
        return getSequence("autoincrement:" + yamcsInstance + "." + tableName + "." + columnName, true);
    }

    void saveTableDefinition(String yamcsInstance, TableDefinition tblDef,
            List<TableColumnDefinition> keyDef,
            List<TableColumnDefinition> valueDef) throws RocksDBException {
        RdbTable table = tables.get(tblDef);
        if (table == null) {
            throw new IllegalArgumentException("This is not a table I know");
        }

        ProtoTableDefinition rtd = TableDefinitionSerializer.toProtobuf(tblDef, keyDef, valueDef);
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder();
        trb.setType(Type.TABLE_DEFINITION);
        trb.setTableDefinition(rtd);
        trb.setTableName(tblDef.getName());

        trb.setTbsIndex(table.tbsIndex);
        updateRecord(yamcsInstance, trb);
    }

    /**
     * @throws YarchException
     * 
     */
    void migrateTableDefinition(String yamcsInstance, TableDefinition tblDef) throws RocksDBException, YarchException {
        if ("alarms".equals(tblDef.getName())) {
            changePvColumnType(tblDef, ParameterAlarmStreamer.CNAME_TRIGGER);
            changePvColumnType(tblDef, ParameterAlarmStreamer.CNAME_CLEAR);
            changePvColumnType(tblDef, ParameterAlarmStreamer.CNAME_SEVERITY_INCREASED);
        } else if (EventRecorder.TABLE_NAME.equals(tblDef.getName())) {
            changeEventColumnType(tblDef, "body");
        } else if ("event_alarms".equals(tblDef.getName())) {
            changeEventColumnType(tblDef, EventAlarmStreamer.CNAME_TRIGGER);
            changeEventColumnType(tblDef, EventAlarmStreamer.CNAME_CLEAR);
            changeEventColumnType(tblDef, EventAlarmStreamer.CNAME_SEVERITY_INCREASED);
        }
        createTable(yamcsInstance, tblDef);
    }

    private void changePvColumnType(TableDefinition tblDef, String cname) {

        log.info("Changing data type of column {} to {}", cname, DataType.PARAMETER_VALUE);
        tblDef.changeDataType(cname, DataType.PARAMETER_VALUE);
    }

    private void changeEventColumnType(TableDefinition tblDef, String cname) {
        log.info("Changing data type of column {} to {}", cname, Db.Event.class.getName());
        tblDef.changeDataType(cname, DataType.protobuf(Db.Event.class.getName()));
    }

    void dropTable(TableDefinition tblDef) throws RocksDBException, IOException {

        List<RdbTableWriter> l = null;
        synchronized (tableWriters) {
            l = tableWriters.remove(tblDef);
        }

        if (l != null) {
            for (RdbTableWriter w : l) {
                w.close();
            }
        }
        // TODO make atomic as much as possible
        synchronized (tables) {
            RdbTable table = tables.remove(tblDef);
            if (table == null) {
                throw new IllegalArgumentException("Unknown table " + tblDef.getName());
            }

            // remove the secondary index definition and data (if any)
            for (TablespaceRecord tr : filter(Type.SECONDARY_INDEX, table.yamcsInstance,
                    tr -> tr.getTableName().equals(tblDef.getName()))) {
                int tbsIndex = tr.getTbsIndex();
                log.debug("Removing secondary index {}", tr);
                removeTbsIndex(Type.SECONDARY_INDEX, tbsIndex);
            }

            // remove data from partitions
            for (Partition p : table.partitionManager.getPartitions()) {
                RdbPartition rdbp = (RdbPartition) p;

                int tbsIndex = rdbp.tbsIndex;
                log.debug("Removing tbsIndex {}", tbsIndex);
                YRDB db = getRdb(rdbp.dir, false);
                db.getDb().deleteRange(dbKey(tbsIndex), dbKey(tbsIndex + 1));
                removeTbsIndex(Type.TABLE_PARTITION, tbsIndex);
            }

            // remove the histogram data (if any)
            for (TablespaceRecord tr : filter(Type.HISTOGRAM, table.yamcsInstance,
                    tr -> tr.getTableName().equals(tblDef.getName()))) {
                int tbsIndex = tr.getTbsIndex();
                log.debug("Removing histogram data {}", tr);
                removeTbsIndex(Type.HISTOGRAM, tbsIndex);
            }

            // remove table definition and data from main db
            removeTbsIndex(Type.TABLE_DEFINITION, table.tbsIndex);
        }
    }

    /**
     * returns the table associated to this definition or null if this table is not known.
     * 
     * @param tblDef
     * @return
     */
    public RdbTable getTable(TableDefinition tblDef) {
        return tables.get(tblDef);
    }

    /**
     * Called at instance start to load all tables for the instance
     * 
     * @throws YarchException
     */
    List<TableDefinition> loadTables(String yamcsInstance) throws RocksDBException, IOException, YarchException {
        List<TableDefinition> list = new ArrayList<>();

        for (TablespaceRecord tr : filter(Type.TABLE_DEFINITION, yamcsInstance, tr -> true)) {
            if (!tr.hasTableName()) {
                throw new DatabaseCorruptionException(
                        "Found table definition metadata record without a table name:" + tr);
            }

            TableDefinition tblDef = TableDefinitionSerializer.fromProtobuf(tr.getTableDefinition());
            tblDef.setName(tr.getTableName());

            String cfName = tblDef.getCfName() == null ? YRDB.DEFAULT_CF : tblDef.getCfName();

            RdbTable table = new RdbTable(yamcsInstance, this, tblDef, tr.getTbsIndex(), cfName);
            tables.put(tblDef, table);
            table.readPartitions();

            configureAutoincrementSequences(yamcsInstance, tblDef);

            list.add(tblDef);
            log.debug("Loaded table {}", tblDef);
        }
        log.info("Loaded {} tables for instance {}", list.size(), yamcsInstance);
        return list;
    }

    public TableWalker newTableWalker(ExecutionContext ctx, TableDefinition tblDef,
            boolean ascending, boolean follow) {
        if (!tables.containsKey(tblDef)) {
            throw new IllegalArgumentException("Unknown table '" + tblDef.getName() + "'");
        }
        ctx.setTablespace(this);
        RdbTableWalker rrs = new RdbTableWalker(ctx, tblDef, ascending, follow);
        walkers.put(rrs, DUMMY);
        return rrs;
    }

    public RdbTableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tblDef, InsertMode insertMode) {
        synchronized (tables) {
            RdbTable table = tables.get(tblDef);

            if (table == null) {
                throw new IllegalArgumentException("Unknown table '" + tblDef.getName() + "'");
            }

            RdbTableWriter writer = new RdbTableWriter(ydb, table, insertMode);
            synchronized (tableWriters) {
                List<RdbTableWriter> l = tableWriters.computeIfAbsent(tblDef, t -> new ArrayList<>());
                l.add(writer);
            }
            writer.closeFuture().thenAccept(v -> writerClosed(tblDef, writer));
            return writer;
        }
    }

    private void writerClosed(TableDefinition tblDef, RdbTableWriter writer) {
        synchronized (tableWriters) {
            List<RdbTableWriter> l = tableWriters.get(tblDef);
            if (l != null) {
                l.remove(writer);
            }
        }
    }

    public void close() {
        for (TableWalker rrs : walkers.keySet()) {
            rrs.close();
        }
        synchronized (sequences) {
            for (RdbSequence seq : sequences.values()) {
                seq.close();
            }
            sequences.clear();
        }
        rdbFactory.shutdown();
    }

    public Sequence getSequence(String name, boolean create) throws YarchException, RocksDBException {
        synchronized (sequences) {
            RdbSequence seq = sequences.get(name);
            if (seq == null && (create || mainDb.get(cfMetadata, RdbSequence.getDbKey(name)) != null)) {
                seq = new RdbSequence(name, mainDb, cfMetadata);
                sequences.put(name, seq);
            }
            return seq;
        }
    }

    public void renameTable(String yamcsInstance, TableDefinition tblDef, String newName) throws RocksDBException {
        synchronized (tables) {
            RdbTable table = tables.get(tblDef);
            if (table == null) {
                throw new IllegalArgumentException("Unknown table '" + tblDef.getName() + "'");
            }
            String oldName = tblDef.getName();
            List<TablespaceRecord> trList = getTableRecords(yamcsInstance, oldName, Type.TABLE_DEFINITION,
                    Type.TABLE_PARTITION, Type.HISTOGRAM, Type.SECONDARY_INDEX)
                            .stream().map(tr -> tr.toBuilder().setTableName(newName).build())
                            .collect(Collectors.toList());

            try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                for (TablespaceRecord tr : trList) {
                    wb.put(cfMetadata, getMetadataKey(tr.getType(), tr.getTbsIndex()), tr.toByteArray());
                }
                mainDb.write(wo, wb);
            }
            tblDef.setName(newName);
        }

    }

    private List<TablespaceRecord> getTableRecords(String yamcsInstance, String tblName, Type... types) {
        List<TablespaceRecord> trList = new ArrayList<>();
        for (Type type : types) {
            trList.addAll(filter(type, yamcsInstance, tr -> tblName.equals(tr.getTableName())));
        }
        return trList;
    }

    ScheduledThreadPoolExecutor getExecutor() {
        return executor;
    }

    public TableWalker newSecondaryIndexTableWalker(YarchDatabaseInstance ydb, TableDefinition tblDef,
            boolean ascending, boolean follow) {

        TableWalker tw = new SecondaryIndexTableWalker(this, verifyTable(tblDef), ascending, follow);
        walkers.put(tw, DUMMY);
        return tw;
    }

    private RdbTable verifyTable(TableDefinition tblDef) {
        RdbTable table = tables.get(tblDef);

        if (table == null) {
            throw new IllegalArgumentException("Unknown table '" + tblDef.getName() + "'");
        }
        return table;
    }

    public List<SequenceInfo> getSequencesInfo() {
        List<SequenceInfo> seqlist = new ArrayList<>();
        // first add the open sequences
        synchronized (sequences) {
            for (Map.Entry<String, RdbSequence> me : sequences.entrySet()) {
                seqlist.add(new SequenceInfo(me.getKey(), me.getValue().get()));
            }
        }
        // then the not open ones
        byte[] prefix = new byte[] { METADATA_FB_SEQ };
        try (AscendingRangeIterator it = new AscendingRangeIterator(mainDb.newIterator(cfMetadata), prefix, prefix)) {
            while (it.isValid()) {
                String name = RdbSequence.getName(it.key());
                if (!seqlist.stream().anyMatch(s -> name.equals(s.getName()))) {
                    seqlist.add(new SequenceInfo(name, RdbSequence.getValue(it.value())));
                }
                it.next();
            }
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
        return seqlist;
    }

    RocksdbMemoryUsage getApproximateMemoryUsage() {
        var lruCache = RdbConfig.getInstance().getTablespaceConfig(name).getTableCache();
        List<YRDB> dbList = rdbFactory.getOpenDbs(false);
        var mbt = MemoryUtil.getApproximateMemoryUsageByType(rdbFactory.getOpenRdbs(), null);
        dbList.forEach(yrdb -> rdbFactory.dispose(yrdb));
        RocksdbMemoryUsage memUsage = new RocksdbMemoryUsage();

        memUsage.blockCacheMemoryUsage = lruCache.getUsage();
        memUsage.indexMemoryUsage = mbt.get(MemoryUsageType.kTableReadersTotal);
        memUsage.memtableMemoryUsage = mbt.get(MemoryUsageType.kMemTableTotal);
        memUsage.pinnedBlocksMemoryUsage = lruCache.getPinnedUsage();

        return memUsage;
    }

    static class RocksdbMemoryUsage {
        long blockCacheMemoryUsage;
        long indexMemoryUsage;
        long memtableMemoryUsage;
        long pinnedBlocksMemoryUsage;

        @Override
        public String toString() {
            return "RocksdbMemoryUsage [blockCacheMemoryUsage=" + blockCacheMemoryUsage + ", indexMemoryUsage="
                    + indexMemoryUsage + ", memtableMemoryUsage=" + memtableMemoryUsage + ", pinnedBlocksMemoryUsage="
                    + pinnedBlocksMemoryUsage + "]";
        }
    }

}
