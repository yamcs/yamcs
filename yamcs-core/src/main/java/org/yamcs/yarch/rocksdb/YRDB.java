package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayWrapper;
import org.yamcs.yarch.rocksdb.RdbConfig.TableConfig;
/**
 * wrapper around RocksDB that keeps track of column families
 * 
 * @author nm
 *
 */
public class YRDB {
    Map<ByteArrayWrapper, ColumnFamilyHandle> columnFamilies=new HashMap<ByteArrayWrapper, ColumnFamilyHandle>();

    private final RocksDB db;
    private boolean isClosed = false;
    private final String path;
    private ColumnFamilySerializer cfSerializer;
    private final ColumnFamilyOptions cfoptions;

    private final DBOptions dbOptions;
    /**
     * Create or open a new RocksDb.
     * 
     * @param dir - if it exists, it has to be a directory 
     * @param cfSerializer - column family serializer
     * @throws RocksDBException
     * @throws IOException 
     */
    YRDB(String dir, ColumnFamilySerializer cfSerializer) throws RocksDBException, IOException {
        this.cfSerializer = cfSerializer;
        File f = new File(dir);
        if(f.exists() && !f.isDirectory()) {
            throw new IOException("'"+dir+"' exists and it is not a directory");
        }
        RdbConfig rdbConfig = RdbConfig.getInstance();
        TableConfig tc = rdbConfig.getTableConfig(f.getName());

        cfoptions = (tc==null)? rdbConfig.getDefaultColumnFamilyOptions():tc.getColumnFamilyOptions();
        Options opt = (tc==null)? rdbConfig.getDefaultOptions():tc.getOptions();
        dbOptions = (tc==null)? rdbConfig.getDefaultDBOptions():tc.getDBOptions();

        this.path = dir;
        File current = new File(dir+File.separatorChar+"CURRENT");
        if(current.exists()) {
            List<byte[]> cfl = RocksDB.listColumnFamilies(opt, dir);

            if(cfl!=null) {
                List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());

                for(byte[] b: cfl) {
                    cfdList.add(new ColumnFamilyDescriptor(b, cfoptions));					
                }
                List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
                db = RocksDB.open(dbOptions, dir, cfdList, cfhList);
                for(int i=0;i<cfl.size();i++) {
                    byte[] b = cfl.get(i);
                    if(!Arrays.equals(b, RocksDB.DEFAULT_COLUMN_FAMILY)) {
                        columnFamilies.put(new ByteArrayWrapper(b), cfhList.get(i));
                    } 
                }

            } else { //no existing column families
                db = RocksDB.open(opt, dir);
            }
        } else {
            //new DB
            db = RocksDB.open(opt, dir);
        }
    }

    /**
     * Close the database. Shall only be done from the RDBFactory
     */
    void close() {		
        db.close();
        isClosed = true;
    }

    /**
     * @return true if the database is open
     */
    public boolean isOpen() {
        return !isClosed;
    }

    public void flush(FlushOptions flushOptions) throws RocksDBException {
        db.flush(flushOptions);		
    }

    public List<RocksIterator> newIterators(List<ColumnFamilyHandle> cfhList, boolean tailing) throws RocksDBException {
        ReadOptions ro = new ReadOptions();
        ro.setTailing(tailing);
        return db.newIterators(cfhList, ro);
    }


    public RocksIterator newIterator(ColumnFamilyHandle cfh) throws RocksDBException {
        return db.newIterator(cfh);
    }

    public synchronized ColumnFamilyHandle getColumnFamilyHandle(Object value) {
        ColumnFamilyHandle cfh = columnFamilies.get(new ByteArrayWrapper(cfSerializer.objectToByteArray(value)));
        
        //in yamcs 0.29.3 and older we used to create a column family for null values (i.e. when not partitioning on a value)
        //starting with yamcs 0.29.4 we use the default column family for this
        // the old tables are still supported because at startup the columnFamilies map will be populated with the null key
        if((value==null) && (cfh==null)) { 
            return db.getDefaultColumnFamily(); 
        }
        return cfh;
    }

    public byte[] get(ColumnFamilyHandle cfh, byte[] key) throws RocksDBException {
        return db.get(cfh, key);
    }

    public synchronized ColumnFamilyHandle createColumnFamily(Object value) throws RocksDBException {
        byte[] b = cfSerializer.objectToByteArray(value);
        ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(b, cfoptions);
        ColumnFamilyHandle cfh = db.createColumnFamily(cfd);			
        columnFamilies.put(new ByteArrayWrapper(b), cfh);
        return cfh;
    }

    public void put(ColumnFamilyHandle cfh, byte[] k, byte[] v) throws RocksDBException {		
        db.put(cfh, k, v);
    }

    public Collection<Object> getColumnFamilies() {
        List<Object> l = new ArrayList<>();
        for(ByteArrayWrapper baw: columnFamilies.keySet()) {
            l.add(cfSerializer.byteArrayToObject(baw.getData()));
        }
        return l;
    }

    public String getPath() { 
        return path;
    }


    public String getProperites() throws RocksDBException {
        if(isClosed) throw new RuntimeException("Database is closed");
        
        final List<String> mlprops = Arrays.asList("rocksdb.stats", "rocksdb.sstables", "rocksdb.cfstats", "rocksdb.dbstats", "rocksdb.levelstats"
                , "rocksdb.aggregated-table-properties");

        final List<String> slprops = Arrays.asList("rocksdb.num-immutable-mem-table",  "rocksdb.num-immutable-mem-table-flushed"
                , "rocksdb.mem-table-flush-pending",  "rocksdb.num-running-flushes" , "rocksdb.compaction-pending", "rocksdb.num-running-compactions", "rocksdb.background-errors",  "rocksdb.cur-size-active-mem-table"
                , "rocksdb.cur-size-all-mem-tables", "rocksdb.size-all-mem-tables", "rocksdb.num-entries-active-mem-table",  "rocksdb.num-entries-imm-mem-tables"
                , "rocksdb.num-deletes-active-mem-table",  "rocksdb.num-deletes-imm-mem-tables", "rocksdb.estimate-num-keys", "rocksdb.estimate-table-readers-mem"
                , "rocksdb.is-file-deletions-enabled" ,  "rocksdb.num-snapshots","rocksdb.oldest-snapshot-time" ,  "rocksdb.num-live-versions"
                , "rocksdb.current-super-version-number", "rocksdb.estimate-live-data-size", "rocksdb.base-level");
        
        
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<ByteArrayWrapper, ColumnFamilyHandle> e: columnFamilies.entrySet()) {
            Object o = cfSerializer.byteArrayToObject(e.getKey().getData());
            ColumnFamilyHandle chf = e.getValue();
            sb.append("============== Column Family: "+o+"========\n");
            for(String p:slprops) {
                sb.append(p).append(": ");
                sb.append(db.getProperty(chf, p));
                sb.append("\n");
            }
            for(String p:mlprops) {
                sb.append("---------- "+p+"----------------\n");
                sb.append(db.getProperty(chf, p));
                sb.append("\n");
           
            }
        }
        return sb.toString();
    }

    public RocksDB getDb() {
        return db;
    }

    public synchronized void dropColumnFamily(ColumnFamilyHandle cfh) throws RocksDBException {
        for(Map.Entry<ByteArrayWrapper, ColumnFamilyHandle> e: columnFamilies.entrySet()) {
            if(e.getValue()==cfh) {
                db.dropColumnFamily(cfh);
                columnFamilies.remove(e.getKey());
                break;
            }
        }
    }

    public ColumnFamilySerializer getColumnFamilySerializer() {
        return cfSerializer;
    }
    
    public void setColumnFamilySerializer(ColumnFamilySerializer cfs) {
        this.cfSerializer = cfs;
    }
    
}
