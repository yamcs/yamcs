package org.yamcs.yarch.rocksdb;

import java.io.File;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.IntArray;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import com.google.protobuf.InvalidProtocolBufferException;

import static org.yamcs.utils.ByteArrayUtils.decodeInt;
import static org.yamcs.utils.ByteArrayUtils.encodeInt;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

/**
 * 
 * Tablespaces are rocksdb databases normally stored in the yamcs data directory (/storage/yamcs-data). Each corresponds
 * to a directory &lt;tablespace-name&gt;.rdb and has a definition file tablespace-name.tbs.
 * 
 * Tablespaces can also have time based partitions in different RocksDB databases in sub-directories such as
 * &lt;tablespace-name&gt;.rdb/YYYY/
 * 
 * There are two column families in the main database: - the _metadata_ column family - contains metadata. - the default
 * column family - contains data.
 * 
 * The data is partitioned by the first 4 bytes of the key which we call tbsIndex.
 * 
 * One tbsIndex corresponds to a so called tablespace record. For example tbsIndex=5 can correspond to all telemetry
 * packets for packet XYZ.
 * 
 * Except for the 4 bytes tbsIndex, the rest of the key and value are completely dependent on the data type. For example
 * for yarch table data, the rest of key following the 4 bytes tbsIndex represents the key of the row in the table.
 * 
 * The metadata contains two types of records, identified by the first byte of the key: - key: 0x1 value: 1 byte version
 * number (0x1), 4 bytes max tbsIndex used to store the max tbsIndex and also stores a version number in case the format
 * will change in the future
 * 
 * - key: 0x2, 1 byte record type, 4 bytes tbsIndex value: protobuf encoded TablespaceRecord used to store what
 * information corresponds to each tbsIndex the record type corresponds to the Type enumerations from tablespace.proto
 * In tablespace.proto for a description of the possible record types.
 * 
 * The metadata
 * 
 */
public class Tablespace {
    static Logger log = LoggerFactory.getLogger(Tablespace.class.getName());

    // unique name for this tablespace
    private final String name;

    private String customDataDir;
    private static final String CF_METADATA = "_metadata_";
    private static final byte METADATA_VERSION = 1;

    private static final byte[] METADATA_KEY_MAX_TBS_VERSION = new byte[] { 1 };
    private static final byte METADATA_TR = 2; // first byte of metadata records
                                               // keys that contain tablespace
                                               // records

    YRDB db;
    ColumnFamilyHandle cfMetadata;
    long maxTbsIndex;

    RDBFactory rdbFactory;

    public Tablespace(String name) {
        this.name = name;
    }

    public void loadDb(boolean readonly) throws IOException {
        String dbDir = getDataDir();
        rdbFactory = RDBFactory.getInstance(dbDir);
        File f = new File(dbDir + "/CURRENT");
        try {
            if (f.exists()) {
                log.debug("Opening existing database {}", dbDir);
                db = rdbFactory.getRdb(readonly);
                cfMetadata = db.getColumnFamilyHandle(CF_METADATA);
                if (cfMetadata == null) {
                    throw new IOException("Existing tablespace database '" + dbDir
                            + "' does not contain a column family named '" + CF_METADATA);
                }

                byte[] value = db.get(cfMetadata, METADATA_KEY_MAX_TBS_VERSION);
                if (value == null) {
                    throw new DatabaseCorruptionException(
                            "No (version, maxTbsIndex) record found in the metadata");
                }
                if (value[0] != METADATA_VERSION) {
                    throw new DatabaseCorruptionException(
                            "Wrong metadata version " + value[0] + " expected " + METADATA_VERSION);
                }
                maxTbsIndex = Integer.toUnsignedLong(decodeInt(value, 1));
                log.info("Opened tablespace database {}, num records:{}, num metadata records: {}, maxTbsIndex: {}",
                        dbDir,
                        db.getApproxNumRecords(), db.getApproxNumRecords(cfMetadata), maxTbsIndex);
            } else {
                if (readonly) {
                    throw new IllegalStateException("Cannot create a new db when readonly is set to true");
                }
                log.info("Creating database at {}", dbDir);
                db = rdbFactory.getRdb(readonly);
                cfMetadata = db.createColumnFamily(CF_METADATA);
                initMaxTbsIndex();
            }
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
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
            throws RocksDBException, DatabaseCorruptionException {
        List<TablespaceRecord> r = new ArrayList<>();
        byte[] rangeStart = new byte[] { METADATA_TR, (byte) type.getNumber() };

        try (AscendingRangeIterator arit = new AscendingRangeIterator(db.newIterator(cfMetadata), rangeStart, false,
                rangeStart, false)) {
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
                            r.add(tr.build());
                        }
                    } else {
                        if (instanceName.equals(name)) {
                            tr.setInstanceName(name);
                            r.add(tr.build());
                        }
                    }
                }
                arit.next();
            }
        }
        return r;
    }

    /**
     * Creates a new tablespace record and adds it to the metadata database
     * 
     * @param trb
     *            - the builder has to have all fields set except for the tbsIndex which will be assigned by this method
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
        db.put(cfMetadata, getMetadataKey(tr.getType(), tbsIndex), tr.toByteArray());
        return tr;
    }

    TablespaceRecord updateRecord(String yamcsInstance, WriteBatch writeBatch, TablespaceRecord.Builder trb) {
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
        log.debug("Adding new metadata record {}", tr);
        writeBatch.put(cfMetadata, getMetadataKey(tr.getType(), tr.getTbsIndex()), tr.toByteArray());
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
        byte[] v = new byte[5];
        v[0] = METADATA_VERSION;
        encodeInt((int) maxTbsIndex, v, 1);
        db.put(cfMetadata, METADATA_KEY_MAX_TBS_VERSION, v);
        return (int) maxTbsIndex;
    }

    /**
     * (Creates) and returns a database in the given partition directory. If the directory is null, return then main
     * tablespace db
     * 
     * @param partitionDir
     * @param readOnly
     * @throws IOException
     */
    public YRDB getRdb(String partitionDir, boolean readOnly) throws IOException {
        if (partitionDir == null) {
            return db;
        } else {
            return rdbFactory.getRdb(partitionDir, readOnly);
        }
    }

    public YRDB getRdb(String relativePath) throws IOException {
        return getRdb(relativePath, false);
    }

    /**
     * Get the main database of the tablespace
     */
    public YRDB getRdb() {
        return db;
    }

    public void dispose(YRDB rdb) {
        if (db == rdb) {
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
            dir = YarchDatabase.getDataDir() + "/" + name + ".rdb";
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
            wb.remove(cfMetadata, getMetadataKey(type, tbsIndex));
            byte[] beginKey = new byte[TBS_INDEX_SIZE];
            byte[] endKey = new byte[TBS_INDEX_SIZE];
            ByteArrayUtils.encodeInt(tbsIndex, beginKey, 0);
            ByteArrayUtils.encodeInt(tbsIndex + 1, endKey, 0);
            wb.deleteRange(beginKey, endKey);
            db.getDb().write(wo, wb);
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
                wb.remove(cfMetadata, getMetadataKey(type, tbsIndexArray.get(i)));
                byte[] beginKey = new byte[TBS_INDEX_SIZE];
                byte[] endKey = new byte[TBS_INDEX_SIZE];
                ByteArrayUtils.encodeInt(tbsIndex, beginKey, 0);
                ByteArrayUtils.encodeInt(tbsIndex + 1, endKey, 0);
                wb.deleteRange(beginKey, endKey);
            }
            db.getDb().write(wo, wb);
        }
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
        key[0] = METADATA_TR;
        key[1] = (byte) type.getNumber();
        encodeInt(tbsIndex, key, 2);
        return key;
    }

    public void close() {
        rdbFactory.shutdown();
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
        db.put(key, value);
    }

    public byte[] getData(byte[] key) throws RocksDBException {
        return db.get(key);
    }

    public void remove(byte[] key) throws RocksDBException {
        checkKey(key);
        db.getDb().delete(key);
    }

    private void checkKey(byte[] key) {
        if (key.length < TBS_INDEX_SIZE) {
            throw new IllegalArgumentException("The key has to contain at least the tbsIndex");
        }
    }
}
