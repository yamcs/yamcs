package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksIterator;
import org.yamcs.yarch.AbstractTableReaderStream;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.IndexFilter;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.RawTuple;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;

public class RdbTableReaderStream extends AbstractTableReaderStream implements Runnable, DbReaderStream {
    static AtomicInteger count = new AtomicInteger(0);
    final PartitioningSpec partitioningSpec;
    final RdbPartitionManager partitionManager;
    final TableDefinition tableDefinition;
    private long numRecordsRead = 0;
    
    protected RdbTableReaderStream(YarchDatabase ydb, TableDefinition tblDef, RdbPartitionManager partitionManager, boolean ascending, boolean follow) {
        super(ydb, tblDef, partitionManager, ascending, follow);
        this.tableDefinition = tblDef;
        partitioningSpec = tblDef.getPartitioningSpec();
        this.partitionManager = partitionManager;
    }


    @Override 
    public void start() {
        (new Thread(this, "TcTableReader["+getName()+"]")).start();
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
            ColumnDefinition cd=tableDefinition.getKeyDefinition().getColumn(0);
            ColumnSerializer cs=tableDefinition.getColumnSerializer(cd.getName());
            if(range.keyStart!=null) {
                strictStart=range.strictStart;
                rangeStart=cs.getByteArray(range.keyStart);
            }
            if(range.keyEnd!=null) {
                strictEnd=range.strictEnd;
                rangeEnd=cs.getByteArray(range.keyEnd);
            }
        }
        
        if (ascending) {
            return readAscending(partitions, rangeStart, strictStart, rangeEnd, strictEnd);
        } else {
            return readDescending(partitions, rangeStart, strictStart, rangeEnd, strictEnd);
        }
     
    }
    
    private boolean readAscending(List<Partition> partitions, byte[] rangeStart, boolean strictStart, byte[] rangeEnd, boolean strictEnd) {
        PriorityQueue<RdbRawTuple> orderedQueue=new PriorityQueue<RdbRawTuple>();
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        YRDB rdb = null;
        try {
            RdbPartition p1 = (RdbPartition) partitions.iterator().next();
            String dbDir = p1.dir;
            log.debug("opening database "+ dbDir);
            rdb = rdbFactory.getRdb(tableDefinition.getDataDir()+"/"+p1.dir, new ColumnValueSerializer(tableDefinition.getPartitioningSpec().getValueColumnType()), false);
            List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>();
            for(Partition p: partitions) {
                ColumnFamilyHandle cfh = rdb.getColumnFamilyHandle(p.getValue());
                if(cfh!=null) {
                    cfhList.add(cfh);
                }
            }

            //create a cursor for all partitions
            List<RocksIterator> iteratorList = rdb.newIterators(cfhList, follow);

            int i=0;
            for(RocksIterator it:iteratorList) {
                boolean found=true;
                if(rangeStart!=null) {
                    it.seek(rangeStart);
                    if(it.isValid()) {                  
                        if((strictStart)&&(compare(rangeStart, it.key())==0)) {
                            //if filter condition is ">" we skip the first record if it is equal to the key
                            it.next();
                            found=it.isValid();
                        }
                    } else {
                        found=false;
                    }
                    if(!found) log.debug("no record corresponding to the StartFilter");
                } else {
                    it.seekToFirst();
                    if(!it.isValid()) {
                        log.debug("tcb contains no record");
                        found = false;
                    }
                }
                if(!found) {
                    it.close();                                        
                } else {
                    numRecordsRead++;
                    orderedQueue.add(new RdbRawTuple(it.key(), it.value(), it, i++));
                }
            }
            log.debug("got one tuple from each partition, starting the business");

            //now continue publishing the first element from the priority queue till it becomes empty
            while((!quit) && orderedQueue.size()>0){
                RdbRawTuple rt=orderedQueue.poll();
                if(!emitIfNotPastStop(rt, rangeEnd, strictEnd)) {
                    return true;
                }
                rt.iterator.next();
                if(rt.iterator.isValid()) {
                    numRecordsRead++;
                    rt.key = rt.iterator.key();
                    rt.value = rt.iterator.value();
                    orderedQueue.add(rt);
                } else {
                    log.debug(rt.iterator+" finished");
                    rt.iterator.close();                    
                }
            }
           
            return false;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        } finally {
            for(RdbRawTuple rt:orderedQueue) {
                rt.iterator.close();                
            }
            if(rdb!=null) rdbFactory.dispose(rdb);
        }
    }
    
    private boolean readDescending(List<Partition> partitions, byte[] rangeStart, boolean strictStart, byte[] rangeEnd, boolean strictEnd) {
        PriorityQueue<RdbRawTuple> orderedQueue=new PriorityQueue<RdbRawTuple>(RawTuple.reverseComparator);
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        YRDB rdb = null;
        try {
           
            RdbPartition p1 = (RdbPartition) partitions.iterator().next();
            String dbDir = p1.dir;
            log.debug("opening database "+ dbDir);
            rdb = rdbFactory.getRdb(tableDefinition.getDataDir()+"/"+p1.dir, new ColumnValueSerializer(tableDefinition.getPartitioningSpec().getValueColumnType()), false);
            List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>();

            for(Partition p: partitions) {
                ColumnFamilyHandle cfh = rdb.getColumnFamilyHandle(p.getValue());
                if(cfh!=null) {
                    cfhList.add(cfh);
                }
            }

            //create a cursor for all partitions
            List<RocksIterator> iteratorList = rdb.newIterators(cfhList, false);
            
            int i=0;
            for(RocksIterator it:iteratorList) {
                boolean found=true;
                if(rangeEnd!=null) {
                    //seek moves cursor beyond the match
                    it.seek(rangeEnd);
                    boolean verify=false;
                    if(it.isValid()) {
                        if((strictEnd)||(compare(rangeEnd, it.key())!=0)) {
                            it.prev();
                            verify=true;
                        }
                    } else if (!it.isValid()) { //at end of iterator, check last entry
                        it.seekToLast();
                        verify=true;
                    }
                    
                    if(verify && it.isValid()) {
                        int c=compare(it.key(), rangeEnd);
                        if (c>0) {//don't care about non-strict, covered before
                            it.seek(rangeEnd);
                        }
                    }
                    
                    if(it.isValid()) {
                        if((strictEnd)&&(compare(rangeEnd, it.key())==0)) {
                            //if filter condition is "<" we skip the first record if it is equal to the key
                            it.prev();
                            found=it.isValid();
                        }
                    } else {
                        found=false;
                    }
                    if(!found) log.debug("no record corresponding to the StartFilter");
                } else {
                    it.seekToLast();
                    if(!it.isValid()) {
                        log.debug("rdb contains no record");
                        found=false;
                    }
                }
                if(!found) {
                    it.close();                                        
                } else {
                    orderedQueue.add(new RdbRawTuple(it.key(), it.value(), it, i++));
                }
            }
            
            log.debug("got one tuple from each partition, starting the business");
    
            //now continue publishing the first element from the priority queue till it becomes empty
            while((!quit) && orderedQueue.size()>0){
                RdbRawTuple rt=orderedQueue.poll();
                if(!emitIfNotPastStart(rt, rangeStart, strictStart)) {
                    return true;
                }
                rt.iterator.prev();
                if(rt.iterator.isValid()) {
                    rt.key = rt.iterator.key();
                    rt.value = rt.iterator.value();
                    orderedQueue.add(rt);
                } else {
                    log.debug(rt.iterator+" finished");
                    rt.iterator.close();                    
                }
            }

            return false;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        } finally {
            for(RdbRawTuple rt:orderedQueue) {
                rt.iterator.close();                
            }
            if(rdb!=null) rdbFactory.dispose(rdb);
        }
    }
    

    public long getNumRecordsRead() {
        return numRecordsRead;
    }

    class RdbRawTuple extends RawTuple {       
        int index;//used for sorting tuples with equals keys
        RocksIterator iterator;
        byte[] key;
        byte[] value;

        public RdbRawTuple(byte[] key, byte[] value, RocksIterator iterator, int index) {
            super(index);
            this.iterator = iterator;
            this.key = key;
            this.value = value;
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
}
