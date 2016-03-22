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
import org.yamcs.yarch.rocksdb.RdbConfig.TableConfig;
/**
 * wrapper around RocksDB that keeps track of column families
 * 
 * @author nm
 *
 */
public class YRDB {
    Map<Object, ColumnFamilyHandle> columnFamilies=new HashMap<Object, ColumnFamilyHandle>();
    
    RocksDB db;
    private boolean isClosed = false;
    private final String path;
    private final ColumnFamilySerializer cfSerializer;
    private final ColumnFamilyOptions cfoptions;
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
        DBOptions dbopt = (tc==null)? rdbConfig.getDefaultDBOptions():tc.getDBOptions();
        
        this.path = dir;
        if(f.exists()) {
            List<byte[]> cfl = RocksDB.listColumnFamilies(opt, dir);
            
            if(cfl!=null) {
                List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());
                
                for(byte[] b: cfl) {
                    cfdList.add(new ColumnFamilyDescriptor(b, cfoptions));					
                }
                List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
                db = RocksDB.open(dbopt, dir, cfdList, cfhList);
                for(int i=0;i<cfl.size();i++) {
                    byte[] b = cfl.get(i);
                    if(!Arrays.equals(b, RocksDB.DEFAULT_COLUMN_FAMILY)) {
                        Object value = cfSerializer.byteArrayToObject(b);	
                        columnFamilies.put(value, cfhList.get(i));
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
        ColumnFamilyHandle cfh = columnFamilies.get(value);
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
        ColumnFamilyDescriptor cfd= new ColumnFamilyDescriptor(b, cfoptions);
        ColumnFamilyHandle cfh = db.createColumnFamily(cfd);			
        columnFamilies.put(value, cfh);
        return cfh;
    }

    public void put(ColumnFamilyHandle cfh, byte[] k, byte[] v) throws RocksDBException {		
        db.put(cfh, k, v);
    }

    public Collection<Object> getColumnFamilies() {
        return columnFamilies.keySet();
    }

    public String getPath() { 
        return path;
    }

}
