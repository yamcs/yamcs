package org.yamcs.yarch.rocksdb2;

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
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.yamcs.yarch.rocksdb.RdbConfig;
import org.yamcs.yarch.rocksdb.RdbConfig.TableConfig;

/**
 * wrapper around RocksDB that keeps track of column families
 * 
 * @author nm
 *
 */
public class YRDB {
    Map<String, ColumnFamilyHandle> columnFamilies=new HashMap<String, ColumnFamilyHandle>();

    RocksDB db;
    private boolean isClosed = false;
    private final String path;
    private final ColumnFamilyOptions cfoptions;

    /**
     * Create or open a new RocksDb.
     * 
     * @param dir - if it exists, it has to be a directory 
     * @throws RocksDBException
     * @throws IOException 
     */
    @SuppressWarnings("resource")
    YRDB(String dir, int partitionLength) throws RocksDBException, IOException {
        File f = new File(dir);
        if(f.exists() && !f.isDirectory()) {
            throw new IOException("'"+dir+"' exists and it is not a directory");
        }
        RdbConfig rdbConfig = RdbConfig.getInstance();
        TableConfig tc = rdbConfig.getTableConfig(f.getName());

        cfoptions = (tc==null)? rdbConfig.getDefaultColumnFamilyOptions():tc.getColumnFamilyOptions();
        
        Options opt = (tc==null)? rdbConfig.getDefaultOptions():tc.getOptions();
        
        DBOptions dbopt = (tc==null)? rdbConfig.getDefaultDBOptions():tc.getDBOptions();
      
        if(partitionLength>0) {
            opt.useFixedLengthPrefixExtractor(partitionLength);
            cfoptions.useFixedLengthPrefixExtractor(partitionLength);
        } 
        
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
                db = RocksDB.open(dbopt, dir, cfdList, cfhList); 
                for(int i =0; i<cfhList.size(); i++) {
                    ColumnFamilyDescriptor cfd = cfdList.get(i);
                    columnFamilies.put(new String(cfd.columnFamilyName(), StandardCharsets.UTF_8), cfhList.get(i));
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
    public Collection<String> getColumnFamilies() {
        return columnFamilies.keySet();
    }

    public String getPath() { 
        return path;
    }

    public byte[] get(byte[] key) throws RocksDBException {
        return db.get(key);
    }

    public void put(byte[] k, byte[] v) throws RocksDBException {
        db.put(k, v);
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
                if(!it.isValid()) break;

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

    static class IteratorWithSnapshot {
        List<RocksIterator> itList;
        Snapshot snapshot;
    }

    public ColumnFamilyHandle getColumnFamilyHandle(String colName) {
        return columnFamilies.get(colName);
    }

    public byte[] get(ColumnFamilyHandle cfh, byte[] segkey) throws RocksDBException {
        return db.get(cfh, segkey);
    }

    public ColumnFamilyHandle createColumnFamily(String colName) throws RocksDBException {
        ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(colName.getBytes(StandardCharsets.UTF_8));
        ColumnFamilyHandle cfh = db.createColumnFamily(cfd);

        columnFamilies.put(colName, cfh);
        return cfh;
    }

    public void put(ColumnFamilyHandle cfh, byte[] key, byte[] value) throws RocksDBException {
        db.put(cfh, key, value);
    }

    public RocksIterator newIterator(ColumnFamilyHandle cfh) {
        return db.newIterator(cfh);
    }
}
