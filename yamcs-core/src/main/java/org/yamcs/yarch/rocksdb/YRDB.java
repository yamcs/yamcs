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

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayWrapper;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.rocksdb.RdbConfig.TableConfig;
/**
 * wrapper around RocksDB that keeps track of column families
 * 
 * @author nm
 *
 */
public class YRDB {    
    //keep mapping from raw byte array and the object that is used by some applications
    Map<ByteArrayWrapper, ColumnFamilyHandle> columnFamilies = new HashMap<>();
 
    

    private final RocksDB db;
    private boolean isClosed = false;
    private final String path;
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
    YRDB(String dir) throws RocksDBException, IOException {
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
                    columnFamilies.put(new ByteArrayWrapper(b), cfhList.get(i));
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


    public List<RocksIterator> newIterators(List<ColumnFamilyHandle> cfhList, boolean tailing) throws RocksDBException {
        ReadOptions ro = new ReadOptions();
        ro.setTailing(tailing);
        return db.newIterators(cfhList, ro);
    }


    public RocksIterator newIterator(ColumnFamilyHandle cfh) throws RocksDBException {
        return db.newIterator(cfh);
    }

    public synchronized ColumnFamilyHandle getColumnFamilyHandle(byte[] cfname) {
        ColumnFamilyHandle cfh = columnFamilies.get(new ByteArrayWrapper(cfname));
        
        //in yamcs 0.29.3 and older we used to create a column family for null values (i.e. when not partitioning on a value)
        //starting with yamcs 0.29.4 we use the default column family for this
        // the old tables are still supported because at startup the columnFamilies map will be populated with the null key
        if((cfname==null) && (cfh==null)) { 
            return db.getDefaultColumnFamily(); 
        }
        return cfh;
    }
    public synchronized ColumnFamilyHandle getColumnFamilyHandle(String cfname) {
        ColumnFamilyHandle cfh = columnFamilies.get(new ByteArrayWrapper(cfname.getBytes(StandardCharsets.UTF_8)));
        
        //in yamcs 0.29.3 and older we used to create a column family for null values (i.e. when not partitioning on a value)
        //starting with yamcs 0.29.4 we use the default column family for this
        // the old tables are still supported because at startup the columnFamilies map will be populated with the null key
        if((cfname==null) && (cfh==null)) { 
            return db.getDefaultColumnFamily(); 
        }
        return cfh;
    }

    public byte[] get(ColumnFamilyHandle cfh, byte[] key) throws RocksDBException {
        return db.get(cfh, key);
    }

    public byte[] get(byte[] k)  throws RocksDBException {
        return db.get(k);
    }
    
    public synchronized ColumnFamilyHandle createColumnFamily(byte[] cfname) throws RocksDBException {
        ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(cfname, cfoptions);
        ColumnFamilyHandle cfh = db.createColumnFamily(cfd);			
        columnFamilies.put(new ByteArrayWrapper(cfname), cfh);
        return cfh;
    }
    
    public synchronized ColumnFamilyHandle createColumnFamily(String name) throws RocksDBException {
        return createColumnFamily(name.getBytes(StandardCharsets.UTF_8));
    }
    

    public void put(ColumnFamilyHandle cfh, byte[] k, byte[] v) throws RocksDBException {		
        db.put(cfh, k, v);
    }

    public void put(byte[] k, byte[] v) throws RocksDBException {               
        db.put(k, v);
    }
    public List<byte[]> getColumnFamilies() {
        List<byte[]> l = new ArrayList<>();
        for(ByteArrayWrapper baw: columnFamilies.keySet()) {
            l.add(baw.getData());
        }
        return l;
    }

    public Collection<String> getColumnFamiliesAsStrings() {
        List<String> l = new ArrayList<>();
        for(ByteArrayWrapper baw: columnFamilies.keySet()) {
            l.add(new String(baw.getData(),StandardCharsets.UTF_8));
        }
        return l;
    }

    public String getPath() { 
        return path;
    }


    public String getProperites() throws RocksDBException {
        if(isClosed) {
            throw new IllegalStateException("Database is closed");
        }
        
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
            Object o = cfNameToString(e.getKey().getData());
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
    
    static public String cfNameToString(byte[] cfname) {
       for(byte b: cfname) {
           if(b==0) {
               return "HEX["+StringConverter.arrayToHexString(cfname)+"]";
           }
       }
       return new String(cfname, StandardCharsets.UTF_8);
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

    /**
     * scans and returns a list of all prefixes of specified size 
     * @param size
     * @return list of partitions
     * @throws IOException 
     */
    public List<byte[]> scanPartitions(int size) throws IOException {       
        try (RocksIterator it = db.newIterator()) {
            List<byte[]> l = new ArrayList<byte[]>();
            byte[] k = new byte[size];
            while(true) {
                it.seek(k);
                if(!it.isValid()) {
                    break;
                }

                byte[]found = it.key();
                if(found.length<size) {
                    throw new IOException("Found key smaller than the partition length: "+found.length+" vs "+size+". Database corruption?");
                }
                l.add(Arrays.copyOf(found, size));
                System.arraycopy(found, 0, k, 0, size);
                int i = size-1;
                while(i>=0 && k[i] == -1) {
                    k[i] = 0;
                    i--;
                }
                if(i<0) {
                    break;
                } else {
                    k[i] = (byte) (Byte.toUnsignedInt(k[i])+1);
                }
            }
            it.close();
            return l;
        } 
    }

}
