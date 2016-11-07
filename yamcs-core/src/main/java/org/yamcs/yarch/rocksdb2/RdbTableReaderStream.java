package org.yamcs.yarch.rocksdb2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.AbstractTableReaderStream;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.IndexFilter;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.RawTuple;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb2.YRDB.IteratorWithSnapshot;

public class RdbTableReaderStream extends AbstractTableReaderStream implements Runnable, DbReaderStream {
    static AtomicInteger count = new AtomicInteger(0);
    final PartitioningSpec partitioningSpec;
    final RdbPartitionManager partitionManager;
    final TableDefinition tableDefinition;
    private long numRecordsRead = 0;
    
    // size in bytes of value if partitioned by value
    private final int partitionSize;
    protected RdbTableReaderStream(YarchDatabase ydb, TableDefinition tblDef, RdbPartitionManager partitionManager, boolean ascending, boolean follow) {
        super(ydb, tblDef, partitionManager, ascending, follow);
        this.tableDefinition = tblDef;
        partitioningSpec = tblDef.getPartitioningSpec();
        this.partitionManager = partitionManager;
        DataType dt = partitioningSpec.getValueColumnType();
        if(dt!=null) {
            this.partitionSize = ColumnValueSerializer.getSerializedSize(dt);
        } else {
            this.partitionSize =0;
        }
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
            ColumnDefinition cd = tableDefinition.getKeyDefinition().getColumn(0);
            ColumnSerializer cs = tableDefinition.getColumnSerializer(cd.getName());
            if(range.keyStart!=null) {
                strictStart = range.strictStart;
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
        PriorityQueue<RdbRawTuple> orderedQueue = new PriorityQueue<RdbRawTuple>();
        IteratorWithSnapshot iws = null;
        try {
            RDBFactory rdbf=RDBFactory.getInstance(ydb.getName());
            RdbPartition p1 = (RdbPartition) partitions.iterator().next();
            String dbDir = p1.dir;
            log.debug("opening database "+ dbDir);
            YRDB rdb = rdbf.getRdb(tableDefinition.getDataDir()+"/"+p1.dir, false);
            List<byte[]> partValues = new ArrayList<byte[]>();
            
            for(Partition p: partitions) {
                p1 = (RdbPartition) p;
                partValues.add(p1.binaryValue);
            }
            
            //create a cursor for all partitions
            iws = rdb.newAscendingIterators(partValues, rangeStart, !strictStart, follow);
            int index = 0;
            for(RocksIterator it:iws.itList) {
                numRecordsRead++;
                orderedQueue.add(getRawTuple(it, index++));
            }
            log.debug("got one tuple from each partition, starting the business");

            //publish the first element from the priority queue till it becomes empty
            while((!quit) && orderedQueue.size()>0){
                RdbRawTuple rt = orderedQueue.poll();
                if(!emitIfNotPastStop(rt, rangeEnd, strictEnd)) {
                    return true;
                }
                rt.iterator.next();
                boolean finished = true;
                
                if(rt.iterator.isValid()) {
                    byte[] key = rt.iterator.key();
                    if(ByteArrayUtils.startsWith(key, rt.partition)) {
                        finished = false;
                        numRecordsRead++;
                        rt.key = Arrays.copyOfRange(key, partitionSize, key.length);
                        rt.value = rt.iterator.value();
                        orderedQueue.add(rt);
                    }
                }
                
                if(finished) {
                    log.debug(rt.iterator+" finished");
                    rt.iterator.close();                    
                }
            }

            rdbf.dispose(rdb);
            return false;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        } finally {
            for(RdbRawTuple rt:orderedQueue) {
                rt.iterator.close();                
            }
            if(iws!=null && iws.snapshot!=null) {
                iws.snapshot.close();
            }
        }
    }
    
    private RdbRawTuple getRawTuple(RocksIterator it, int index ) {
        byte[] rdbKey = it.key();
        
        byte[] p = Arrays.copyOf(rdbKey, partitionSize);
        byte[] key = Arrays.copyOfRange(rdbKey, partitionSize, rdbKey.length);
        
        return new RdbRawTuple(p, key, it.value(), it, index);
    }
    
  
    
    private boolean readDescending(List<Partition> partitions, byte[] rangeStart, boolean strictStart, byte[] rangeEnd, boolean strictEnd) {
        PriorityQueue<RdbRawTuple> orderedQueue=new PriorityQueue<RdbRawTuple>(RawTuple.reverseComparator);
        IteratorWithSnapshot iws = null;
        try {
            RDBFactory rdbf=RDBFactory.getInstance(ydb.getName());
            RdbPartition p1 = (RdbPartition) partitions.iterator().next();
            String dbDir = p1.dir;
            log.debug("opening database "+ dbDir);
            YRDB rdb = rdbf.getRdb(tableDefinition.getDataDir()+"/"+p1.dir, false);
            List<byte[]> partValues = new ArrayList<byte[]>();

            for(Partition p: partitions) {
                p1 = (RdbPartition) p;
                partValues.add(p1.binaryValue);
            }
            
            //create a cursor for all partitions
            iws = rdb.newDescendingIterators(partValues, rangeEnd, !strictEnd);
            
            int index = 0;
            for(RocksIterator it:iws.itList) {
                numRecordsRead++;
                orderedQueue.add(getRawTuple(it, index++));
            }
            
            log.debug("got one tuple from each partition, starting the business");
    
            //publish the first element from the priority queue till it becomes empty
            while((!quit) && orderedQueue.size()>0){
                RdbRawTuple rt = orderedQueue.poll();
                if(!emitIfNotPastStart(rt, rangeStart, strictStart)) {
                    return true;
                }
                rt.iterator.prev();
                boolean finished = true;
                if(rt.iterator.isValid()) {
                    byte[] key = rt.iterator.key();
                    if(ByteArrayUtils.startsWith(key,  rt.partition)) {
                        rt.key = Arrays.copyOfRange(key, partitionSize, key.length);
                        rt.value = rt.iterator.value();
                        orderedQueue.add(rt);
                        finished = false;
                    }
                } 
                
                if(!finished) {
                    log.debug(rt.iterator+" finished");
                    rt.iterator.close();                    
                }
            }

            rdbf.dispose(rdb);
            return false;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        } finally {
            for(RdbRawTuple rt:orderedQueue) {
                rt.iterator.close();           
            }
            if(iws!=null && iws.snapshot!=null) {
                iws.snapshot.close();
            }
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
}
