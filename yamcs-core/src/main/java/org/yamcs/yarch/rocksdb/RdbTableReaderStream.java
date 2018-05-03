package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.yamcs.yarch.AbstractTableReaderStream;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.IndexFilter;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.RawTuple;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * reader for tables with PartitionStorage.IN_KEY (the partition is prepended in front of the key)
 * @author nm
 *
 */
public class RdbTableReaderStream extends AbstractTableReaderStream implements Runnable, DbReaderStream {
    static AtomicInteger count = new AtomicInteger(0);
    final PartitioningSpec partitioningSpec;
    final TableDefinition tableDefinition;
    private long numRecordsRead = 0;
    private final Tablespace tablespace;
    
    
    protected RdbTableReaderStream(Tablespace tablespace, YarchDatabaseInstance ydb, TableDefinition tblDef, RdbPartitionManager partitionManager,
            boolean ascending, boolean follow) {
        super(ydb, tblDef, partitionManager, ascending, follow);
        
        this.tablespace = tablespace;
        this.tableDefinition = tblDef;
        partitioningSpec = tblDef.getPartitioningSpec();
    }


    @Override 
    public void start() {
        (new Thread(this, "RdbTableReaderStream["+getName()+"]")).start();
    }


    /**
     * reads a file, sending data only that conform with the start and end filters. 
     * returns true if the stop condition is met
     * 
     * All the partitions are from the same time interval and thus from one single RocksDB database
     * 
     */
    @Override
    protected boolean runPartitions(List<Partition> partitions, IndexFilter range) throws IOException {
       
        byte[] rangeStart=null;
        boolean strictStart=false;
        byte[] rangeEnd=null;
        boolean strictEnd=false;

        if(range!=null) {
            ColumnDefinition cd = tableDefinition.getKeyDefinition().getColumn(0);
            ColumnSerializer cs = tableDefinition.getColumnSerializer(cd.getName());
            if(range.keyStart!=null) {
                strictStart = range.strictStart;
                rangeStart = cs.toByteArray(range.keyStart);
            }
            if(range.keyEnd!=null) {
                strictEnd = range.strictEnd;
                rangeEnd = cs.toByteArray(range.keyEnd);
            }
        }
        
        return runValuePartitions(partitions, rangeStart, strictStart, rangeEnd, strictEnd);
    }

    /*
     * runs value based partitions: the partition value is encoded as the first bytes of the key, so we have to make multiple parallel iterators
     */
    private boolean runValuePartitions(List<Partition> partitions, byte[] rangeStart, boolean strictStart, byte[] rangeEnd, boolean strictEnd) {
        DbIterator iterator = null;
        
        RdbPartition p1 = (RdbPartition) partitions.get(0);
        String dbDir = p1.dir;
        log.debug("opening database {}", dbDir);
        YRDB rdb;
        try {
            rdb = tablespace.getRdb(p1.dir, false);
        } catch (IOException e) {
            log.error("Failed to open database", e);
            return false;
        }
        ReadOptions readOptions = new ReadOptions();
        readOptions.setTailing(follow);
        Snapshot snapshot = null;
        if(!follow) {
            snapshot = rdb.getDb().getSnapshot();
            readOptions.setSnapshot(snapshot);
        }
        
        try {
            List<DbIterator> itList = new ArrayList<>(partitions.size());
            //create an iterator for each partitions
            for(Partition p: partitions) {
                p1 = (RdbPartition) p;
                RocksIterator rocksIt = rdb.getDb().newIterator(readOptions);
                
                DbIterator it = getPartitionIterator(rocksIt, p1.tbsIndex,  ascending, rangeStart, strictStart, rangeEnd, strictEnd);
                
                if(it.isValid()) {
                    itList.add(it);
                } else {
                    it.close();
                }
            }
            if(itList.size()==0) {
                return false;
            } else if(itList.size()==1) {
                iterator = itList.get(0);
            } else {
                iterator = new MergingIterator(itList, ascending?new SuffixAscendingComparator(4):new SuffixDescendingComparator(4) );
            }
            if(ascending) {
                return runAscending(iterator, rangeEnd, strictEnd);
            } else {
                return runDescending(iterator, rangeStart, strictStart);
            }
        } finally {
            if(iterator!=null) {
                iterator.close();
            }
            if(snapshot!=null) {
                snapshot.close();
            }
            readOptions.close();
            tablespace.dispose(rdb);
        }
    }
    
    boolean runAscending(DbIterator iterator, byte[] rangeEnd, boolean strictEnd) {
        while(!quit && iterator.isValid()){
            byte[] dbKey = iterator.key();
            byte[] key = Arrays.copyOfRange(dbKey, 4, dbKey.length);
            if(!emitIfNotPastStop(key, iterator.value(), rangeEnd, strictEnd)) {
                return true;
            }
            iterator.next();
        }
        return false;
    }
    
    boolean runDescending(DbIterator iterator, byte[] rangeStart, boolean strictStart) {
        while(!quit && iterator.isValid()){
            byte[] dbKey = iterator.key();
            byte[] key = Arrays.copyOfRange(dbKey, 4, dbKey.length);
            if(!emitIfNotPastStart(key, iterator.value(), rangeStart, strictStart)) {
                return true;
            }
            iterator.prev();
        }
        return false;
    }
   /*
    * create a ranging iterator for the given partition
    * TODO: check usage of RocksDB prefix iterators
    *  
    */
   private DbIterator getPartitionIterator(RocksIterator it, int tbsIndex, boolean ascending, byte[] rangeStart, boolean strictStart,
           byte[] rangeEnd, boolean strictEnd) {
       byte[] dbKeyStart;
       byte[] dbKeyEnd;
       boolean dbStrictStart, dbStrictEnd;
       
       if(rangeStart!=null) {
           dbKeyStart = RdbStorageEngine.dbKey(tbsIndex, rangeStart);
           dbStrictStart = strictStart;
       } else {
           dbKeyStart = RdbStorageEngine.dbKey(tbsIndex);
           dbStrictStart = false;
       }
       
       if(rangeEnd!=null) {
           dbKeyEnd = RdbStorageEngine.dbKey(tbsIndex, rangeEnd);
           dbStrictEnd = strictEnd;
       } else {
           dbKeyEnd = RdbStorageEngine.dbKey(tbsIndex+1);
           dbStrictEnd = true;
       }
       if(ascending) {
           return new AscendingRangeIterator(it, dbKeyStart, dbStrictStart, dbKeyEnd, dbStrictEnd);
       } else {
           return new DescendingRangeIterator(it, dbKeyStart, dbStrictStart, dbKeyEnd, dbStrictEnd);
       }
   }
 
    public long getNumRecordsRead() {
        return numRecordsRead;
    }

    
    class RdbRawTuple extends RawTuple {       
        int index; //used for sorting tuples with equals keys
        RocksIterator iterator;
        byte[] partition;
        byte[] key;
        byte[] value;
        
        public RdbRawTuple(byte[] partition, byte[] key, byte[] value, RocksIterator iterator, int index) {
            super(index);
            this.partition = partition;
            this.key = key;
            this.value = value;
            this.iterator = iterator;
        }

        @Override
        protected byte[] getKey() {
            return key;
        }

        @Override
        protected byte[] getValue() {
            return value;
        }
    }  
    
    static class SuffixAscendingComparator implements Comparator<byte[]> {
        int prefixSize;
        public SuffixAscendingComparator(int prefixSize) {
            this.prefixSize = prefixSize;
        }

        @Override
        public int compare(byte[] b1, byte[] b2) {
            int minLength = Math.min(b1.length, b2.length);
            for (int i = prefixSize; i < minLength; i++) {
                int d=(b1[i]&0xFF)-(b2[i]&0xFF);
                if(d!=0){
                    return d;
                }
            }
            for (int i = 0; i < prefixSize; i++) {
                int d=(b1[i]&0xFF)-(b2[i]&0xFF);
                if(d!=0){
                    return d;
                }
            }
            return b1.length - b2.length;
        }
    }
    
    
    static class SuffixDescendingComparator implements Comparator<byte[]> {
        int prefixSize;
        public SuffixDescendingComparator(int prefixSize) {
            this.prefixSize = prefixSize;
        }

        @Override
        public int compare(byte[] b1, byte[] b2) {
            int minLength = Math.min(b1.length, b2.length);
            for (int i = prefixSize; i < minLength; i++) {
                int d=(b2[i]&0xFF)-(b1[i]&0xFF);
                if(d!=0){
                    return d;
                }
            }
            for (int i = 0; i < prefixSize; i++) {
                int d=(b2[i]&0xFF)-(b1[i]&0xFF);
                if(d!=0){
                    return d;
                }
            }
            return b2.length - b1.length;
        }
    }
}
