package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.MutableColumnFamilyOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.rocksdb.RdbConfig.TablespaceConfig;

/**
 * wrapper around RocksDB that keeps track of column families. It also maintains a reference count and last access
 * timeused by the RdbFactory to close the database if not used for a while
 * 
 * @author nm
 *
 */
public class YRDB {
    Map<String, ColumnFamilyHandle> columnFamilies = new HashMap<>();

    private final RocksDB db;
    private volatile boolean closed = false;
    private final String path;
    public static final String DEFAULT_CF = new String(RocksDB.DEFAULT_COLUMN_FAMILY, StandardCharsets.UTF_8);

    static final String ROCKS_PROP_NUM_KEYS = "rocksdb.estimate-num-keys";
    // keep track
    int refcount = 0;
    long lastAccessTime;

    private final DBOptions dbOptions;
    private final TablespaceConfig tablespaceConfig;

    // locks used for row locking
    static final int NUM_LOCKS = 1 << 4;
    private final Lock[] locks = new Lock[NUM_LOCKS];

    /**
     * Create or open a new RocksDb.
     * 
     * @param dir
     *            if it exists, it has to be a directory
     * @param cfSerializer
     *            column family serializer
     * @throws RocksDBException
     * @throws IOException
     */
    YRDB(String dir, boolean readonly) throws RocksDBException, IOException {
        File f = new File(dir);
        if (f.exists()) {
            if (!f.isDirectory()) {
                throw new IOException("'" + dir + "' exists and it is not a directory");
            }
        } else {
            if (readonly) {
                throw new IllegalArgumentException(
                        "The database does not exist but readonly is requested; cannot create a database when readonly is true");
            }
            if (!f.mkdirs()) {
                throw new IOException("Cannot create directory '" + dir + "'");
            }
        }
        RdbConfig rdbConfig = RdbConfig.getInstance();
        String tblSpaceName = f.getName().replace(".rdb", "");
        tablespaceConfig = rdbConfig.getTablespaceConfig(tblSpaceName);

        dbOptions = tablespaceConfig.getDBOptions();
        this.path = dir;
        File current = new File(dir + File.separatorChar + "CURRENT");
        if (current.exists()) {
            List<byte[]> cfl = RocksDB.listColumnFamilies(new Options(), dir);

            if (cfl != null) {
                List<ColumnFamilyDescriptor> cfdList = new ArrayList<>(cfl.size());

                for (byte[] b : cfl) {
                    String cfName = cfName(b);
                    ColumnFamilyOptions cfOptions = tablespaceConfig.getColumnFamilyOptions(cfName);
                    cfdList.add(new ColumnFamilyDescriptor(b, cfOptions));
                }
                List<ColumnFamilyHandle> cfhList = new ArrayList<>(cfl.size());
                db = RocksDB.open(dbOptions, dir, cfdList, cfhList);
                for (int i = 0; i < cfl.size(); i++) {
                    byte[] b = cfl.get(i);

                    columnFamilies.put(cfName(b), cfhList.get(i));
                }

            } else { // no existing column families. Is it ever the case??
                throw new IllegalStateException(
                        "Found a directory containing a CURRENT file but no database inside? " + dir);
            }
        } else {
            // new DB
            List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
            List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY,
                    tablespaceConfig.getColumnFamilyOptions(DEFAULT_CF)));

            db = RocksDB.open(dbOptions, dir, columnFamilyDescriptors, columnFamilyHandles);
            columnFamilies.put(DEFAULT_CF, db.getDefaultColumnFamily());

        }
        for (int i = 0; i < NUM_LOCKS; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    /**
     * Close the database. Shall only be done from the RDBFactory
     */
    void close() {
        closed = true;
        for (ColumnFamilyHandle cfh : columnFamilies.values()) {
            cfh.close();
        }
        db.close();
    }

    /**
     * @return true if the database is open
     */
    public boolean isOpen() {
        return !closed;
    }

    public List<RocksIterator> newIterators(List<ColumnFamilyHandle> cfhList, boolean tailing) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        ReadOptions ro = new ReadOptions();
        ro.setTailing(tailing);
        return db.newIterators(cfhList, ro);
    }

    public RocksIterator newIterator() throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return db.newIterator();
    }

    public RocksIterator newIterator(ColumnFamilyHandle cfh) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        if (cfh == null) {
            return db.newIterator();
        } else {
            return db.newIterator(cfh);
        }
    }

    public ColumnFamilyHandle getDefaultColumnFamilyHandle() {
        return db.getDefaultColumnFamily();
    }

    public synchronized ColumnFamilyHandle getColumnFamilyHandle(String cfname) {
        return columnFamilies.get(cfname);
    }

    public synchronized ColumnFamilyHandle createAndGetColumnFamilyHandle(String cfname) throws RocksDBException {
        ColumnFamilyHandle cfh = columnFamilies.get(cfname);
        if (cfh == null) {
            cfh = createColumnFamily(cfname);
        }
        return cfh;
    }

    /**
     * 
     * Removes all data belonging to the column family
     * <p>
     * If the column family does not exist returns without doing anything
     */
    public synchronized void dropColumnFamily(String cfname) throws RocksDBException {
        ColumnFamilyHandle cfh = columnFamilies.get(cfname);
        if (cfh != null) {
            db.dropColumnFamily(cfh);
            columnFamilies.remove(cfname);
        }
    }

    public byte[] get(ColumnFamilyHandle cfh, byte[] key) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        if (cfh != null) {
            return db.get(cfh, key);
        } else {
            return db.get(key);
        }
    }

    /**
     * {@link RocksDB#get}
     */
    public byte[] get(byte[] k) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return db.get(k);
    }

    public synchronized ColumnFamilyHandle createColumnFamily(String name) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        var cfoptions = tablespaceConfig.getColumnFamilyOptions(name);
        ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(cfNameb(name), cfoptions);
        ColumnFamilyHandle cfh = db.createColumnFamily(cfd);
        columnFamilies.put(name, cfh);
        return cfh;
    }

    public void put(ColumnFamilyHandle cfh, byte[] k, byte[] v) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        db.put(cfh, k, v);
    }

    public void put(byte[] k, byte[] v) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        db.put(k, v);
    }

    public void put(ColumnFamilyHandle cfh, WriteOptions writeOpt, byte[] k, byte[] v) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        db.put(cfh, writeOpt, k, v);
    }

    public Collection<String> getColumnFamiliesAsStrings() {
        return columnFamilies.keySet();
    }

    public String getPath() {
        return path;
    }

    public String getProperties() throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }

        final List<String> mlprops = Arrays.asList("rocksdb.stats", "rocksdb.sstables", "rocksdb.cfstats",
                "rocksdb.dbstats", "rocksdb.levelstats", "rocksdb.aggregated-table-properties");

        final List<String> slprops = Arrays.asList("rocksdb.num-immutable-mem-table",
                "rocksdb.num-immutable-mem-table-flushed", "rocksdb.mem-table-flush-pending",
                "rocksdb.num-running-flushes", "rocksdb.compaction-pending", "rocksdb.num-running-compactions",
                "rocksdb.background-errors", "rocksdb.cur-size-active-mem-table", "rocksdb.cur-size-all-mem-tables",
                "rocksdb.size-all-mem-tables", "rocksdb.num-entries-active-mem-table",
                "rocksdb.num-entries-imm-mem-tables", "rocksdb.num-deletes-active-mem-table",
                "rocksdb.num-deletes-imm-mem-tables", ROCKS_PROP_NUM_KEYS, "rocksdb.estimate-table-readers-mem",
                "rocksdb.is-file-deletions-enabled", "rocksdb.num-snapshots", "rocksdb.oldest-snapshot-time",
                "rocksdb.num-live-versions", "rocksdb.current-super-version-number", "rocksdb.estimate-live-data-size",
                "rocksdb.base-level");

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ColumnFamilyHandle> e : columnFamilies.entrySet()) {
            Object o = e.getKey();
            ColumnFamilyHandle chf = e.getValue();
            sb.append("============== Column Family: " + o + " ========\n");
            for (String p : slprops) {
                sb.append(p).append(": ");
                sb.append(db.getProperty(chf, p));
                sb.append("\n");
            }
            for (String p : mlprops) {
                sb.append("---------- " + p + "----------------\n");
                sb.append(db.getProperty(chf, p));
                sb.append("\n");

            }
        }
        return sb.toString();
    }

    static public String cfNameToString(byte[] cfname) {
        for (byte b : cfname) {
            if (b == 0) {
                return "HEX[" + StringConverter.arrayToHexString(cfname) + "]";
            }
        }
        return new String(cfname, StandardCharsets.UTF_8);
    }

    public RocksDB getDb() {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return db;
    }

    public long getApproxNumRecords() throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return db.getLongProperty(ROCKS_PROP_NUM_KEYS);
    }

    public long getApproxNumRecords(ColumnFamilyHandle cfh) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return db.getLongProperty(cfh, ROCKS_PROP_NUM_KEYS);
    }

    public void delete(byte[] k) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        db.delete(k);
    }

    public void delete(ColumnFamilyHandle cfh, byte[] k) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        db.delete(cfh, k);
    }

    /**
     * Returns an iterator that iterates over all elements with key starting with the prefix
     */
    public DbIterator newPrefixIterator(byte[] prefix) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return new AscendingRangeIterator(db.newIterator(), prefix, prefix);
    }

    /**
     * Returns an iterator that iterates in reverse over all elements with key starting with the prefix
     */
    public DbIterator newDescendingPrefixIterator(byte[] prefix) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return new DescendingPrefixIterator(db.newIterator(), prefix);
    }

    /**
     * Lock the key - that is if another thread called this method for that key, wait until someone calls the unlock on
     * the same key.
     * <p>
     * The method is implemented by first selecting a {@link Lock} from an fixed size array based on a hash of the key
     * and performing the {@link Lock#lock()} operation on it.
     * 
     * @param dbKey
     */
    public void lock(byte[] dbKey) {
        Lock l = getLock(dbKey);
        l.lock();
    }

    /**
     * Unlock the key previously locked by {@link #lock(byte[])}
     * 
     * @param dbKey
     */
    public void unlock(byte[] dbKey) {
        Lock l = getLock(dbKey);
        l.unlock();
    }

    private Lock getLock(byte[] dbKey) {
        int h = 0;
        for (int i = 0; i < dbKey.length; i++) {
            h = 31 * h + dbKey[i];
        }
        return locks[h & (NUM_LOCKS - 1)];
    }

    public void write(WriteOptions writeOpts, WriteBatch writeBatch) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        db.write(writeOpts, writeBatch);
    }

    public Snapshot getSnapshot() {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return db.getSnapshot();
    }

    public void releaseSnapshot(Snapshot snapshot) {
        if (!closed) {
            db.releaseSnapshot(snapshot);
        }
    }

    /**
     * Compact the given column family between start and stop
     * <p>
     * Throws an {@link IllegalArgumentException} if there is no column family by the given name
     */
    public void compactRange(String cfName, byte[] start, byte[] stop) throws RocksDBException {

        var cfh = cfName == null ? db.getDefaultColumnFamily() : getColumnFamilyHandle(cfName);

        if (cfh == null) {
            throw new IllegalArgumentException("Unknown column family '" + cfName + "'");
        }

        db.compactRange(cfh, start, stop);
    }

    public void disableAutoCompaction(ColumnFamilyHandle cfh) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder mcfo = MutableColumnFamilyOptions.builder() ;
        mcfo.setDisableAutoCompactions(true);

        db.setOptions(cfh, mcfo.build());
    }

    public void enableAutoCompaction(ColumnFamilyHandle cfh) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder mcfo = MutableColumnFamilyOptions.builder();
        mcfo.setDisableAutoCompactions(false);

        db.setOptions(cfh, mcfo.build());
    }

    public void compactRange(ColumnFamilyHandle cfh) throws RocksDBException {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        if (cfh != null) {
            db.compactRange(cfh);
        } else {
            db.compactRange();
        }
    }

    public static String cfName(byte[] cfb) {
        return new String(cfb, StandardCharsets.UTF_8);
    }

    public static byte[] cfNameb(String cfName) {
        return cfName.getBytes(StandardCharsets.UTF_8);
    }



}
