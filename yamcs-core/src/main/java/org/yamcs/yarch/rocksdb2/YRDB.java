package org.yamcs.yarch.rocksdb2;

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
import org.rocksdb.Snapshot;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb2.RdbConfig.TableConfig;

import com.google.common.primitives.Bytes;

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
    private final ColumnFamilyOptions cfoptions;

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
        DBOptions dbopt = (tc==null)? rdbConfig.getDefaultDBOptions():tc.getDBOptions();

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

    public IteratorWithSnapshot newAscendingIterators(List<byte[]> partitions, byte[] rangeStart, boolean startInclusive, boolean tailing) throws RocksDBException {
        IteratorWithSnapshot iws = new IteratorWithSnapshot();
        iws.itList = new ArrayList<RocksIterator>(partitions.size());
        
        
        
        ReadOptions ro = new ReadOptions();
        ro.setTailing(tailing);
        if(!tailing) {
            iws.snapshot = db.getSnapshot();
            ro.setSnapshot(iws.snapshot);
        }

        for(byte[] p: partitions) {
            RocksIterator it = db.newIterator(ro);
            boolean found = false;
            
            if(rangeStart==null) {
                it.seek(p);
                if(it.isValid() && ByteArrayUtils.startsWith(it.key(), p)) {
                    found = true;
                }
            } else {
                byte[] rdbRangeStart = Bytes.concat(p, rangeStart); 
                it.seek(rdbRangeStart);
                if(it.isValid() && ByteArrayUtils.startsWith(it.key(), p)) {
                    if(startInclusive) {
                        found = true;
                    } else {
                        if(ByteArrayUtils.startsWith(it.key(), rdbRangeStart)) {
                            it.next();
                            found = it.isValid() && ByteArrayUtils.startsWith(it.key(), p);
                        } else {
                            found = true;
                        }
                    }
                }
            }

            if(found) {
                iws.itList.add(it);
            } else {
                it.close();
            }
        }
        return iws;
    }


    public IteratorWithSnapshot newDescendingIterators(List<byte[]> partitions, byte[] rangeEnd, boolean endIncluded) throws RocksDBException {
        IteratorWithSnapshot iws = new IteratorWithSnapshot();
        iws.itList = new ArrayList<RocksIterator>(partitions.size());

        ReadOptions ro = new ReadOptions();
        iws.snapshot = db.getSnapshot();
        ro.setSnapshot(iws.snapshot);
        
       
        for(byte[] p: partitions) {
            RocksIterator it = db.newIterator(ro);
            boolean found = false;
            
            if(rangeEnd!=null) {
                byte[] rdbRangeEnd = Bytes.concat(p, rangeEnd); 
                it.seek(rdbRangeEnd);    //seek moves cursor beyond the match
                
                if(it.isValid()) {
                    if(endIncluded  && Arrays.equals(it.key(), rdbRangeEnd)) {
                        found = true;
                    } else {
                        it.prev();
                    }
                } else { //at end of the table, check last entry
                    it.seekToLast();
                }
                
                if(!found && it.isValid()) {
                    if(ByteArrayUtils.startsWith(it.key(), p)) {
                        found = true;
                    }
                }
            } else {
                byte[] p1 = ByteArrayUtils.plusOne(p);
                it.seek(p1);
                if(it.isValid()) {
                    it.prev();
                } else {
                    it.seekToLast();
                }
                if(it.isValid()) {                    
                    if(it.isValid() && ByteArrayUtils.startsWith(it.key(), p)) {
                        found = true;
                    }
                }
            }
            if(found) {
                iws.itList.add(it);
            } else {
                it.close();
            }
        }
            
        return iws;
    }


    public Collection<Object> getColumnFamilies() {
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
     * @throws YarchException 
     */
    public List<byte[]> scanPartitions(int size) throws IOException {       
        List<byte[]> l = new ArrayList<byte[]>();
        RocksIterator it = db.newIterator();
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

        return l;

    }

    static class IteratorWithSnapshot {
        List<RocksIterator> itList;
        Snapshot snapshot;
    }

}
