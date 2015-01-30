package org.yamcs.yarch.tokyocabinet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.yarch.AbstractStream;
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
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.RelOp;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;
import com.google.common.primitives.UnsignedBytes;

/**
 * Reads Tokyo Cabinet tables
 * @author nm
 *
 */
public class TcTableReaderStream extends AbstractTableReaderStream implements Runnable, DbReaderStream {
    private IndexFilter rangeIndexFilter; //if not null, the replay should run in this range
    private Set<Object> partitionFilter;//if not null, the replay only includes data from these partitions - if the table is partitioned on a non index column

    static AtomicInteger count=new AtomicInteger(0);
    volatile boolean quit=false;
    
    private Tuple lastEmitted;
    final PartitioningSpec partitioningSpec;
    final TcPartitionManager partitionManager;
    
    public TcTableReaderStream(YarchDatabase ydb, TableDefinition tblDef, TcPartitionManager partitionManager) {
        super(ydb, tblDef, partitionManager);        
        partitioningSpec=tblDef.getPartitioningSpec();
        this.partitionManager = partitionManager;
    }

    @Override 
    public void start() {
        (new Thread(this, "TcTableReader["+getName()+"]")).start();
    } 

    /*
     * reads a file, sending data only that conform with the start and end filters. 
     * returns true if the stop condition is met
     */
    protected boolean runPartitions(Collection<Partition> partitions, IndexFilter range) throws IOException {
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
        
        
        log.debug("running partitions "+partitions);
        PriorityQueue<TcRawTuple> orderedQueue=new PriorityQueue<TcRawTuple>();
        TCBFactory tcbf=ydb.getTCBFactory();
        try {
            int i=0;
            //first open all the partitions and collect a tuple from each
            for(Partition p:partitions) {
                log.debug("opening partition "+p);
                YBDB db=tcbf.getTcb(p+".tcb",tableDefinition.isCompressed(),false);
                YBDBCUR cursor=db.openCursor();
                boolean found=true;
                if(rangeStart!=null) {
                    if(cursor.jump(rangeStart)) {
                        if((strictStart)&&(compare(rangeStart, cursor.key())==0)) {
                            //if filter condition is ">" we skip the first record if it is equal to the key
                            found=cursor.next();
                        }
                    } else {
                        found=false;
                    }
                    if(!found) log.debug("no record corresponding to the StartFilter");
                } else {                
                    if(!cursor.first()) {
                        log.debug("tcb contains no record");
                        found=false;
                    }
                }
                if(!found) {
                    db.closeCursor(cursor);
                    tcbf.dispose(db);
                } else {
                    orderedQueue.add(new TcRawTuple(this, cursor.key(), cursor.val(), db, cursor, i++));
                }
            }
            log.debug("got one tuple from each partition, starting the business");

            //now continue publishing the first element from the priority queue till it becomes empty
            while((!quit) && orderedQueue.size()>0){
                TcRawTuple rt=orderedQueue.poll();
                if(!emitIfNotPastStop(rt, rangeEnd, strictEnd)) {
                    return true;
                }
                if(rt.cursor.next()) {
                   rt.key=rt.cursor.key();
                   rt.value=rt.cursor.val();
                   orderedQueue.add(rt);
                } else {
                    log.debug(rt.db.path()+" finished");
                    rt.cursor.close();
                    tcbf.dispose(rt.db);
                }
            }
            return false;
        } catch (Exception e){
           e.printStackTrace();
           return false;
        } finally {
            for(RawTuple rt:orderedQueue) {
                rt.cursor.close();
                tcbf.dispose(rt.db);
            }
        }
    }

    @Override
    public boolean addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException {
        if(tableDefinition.isIndexedByKey(cexpr.getName())) {
            ColumnDefinition cdef=tableDefinition.getColumnDefinition(cexpr.getName());
            Comparable<Object> cv=null;
            try {
                cv=(Comparable<Object>)DataType.castAs(cdef.getType(),value);
            } catch (IllegalArgumentException e){
                throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
            }
            if(rangeIndexFilter==null) rangeIndexFilter=new IndexFilter();
            //TODO FIX to allow multiple ranges
            switch(relOp) {
            case GREATER:
                rangeIndexFilter.keyStart=cv;
                rangeIndexFilter.strictStart=true;
                break;
            case GREATER_OR_EQUAL:
                rangeIndexFilter.keyStart=cv;
                rangeIndexFilter.strictStart=false;
                break;
            case LESS:
                rangeIndexFilter.keyEnd=cv;
                rangeIndexFilter.strictEnd=true;
                break;
            case LESS_OR_EQUAL:
                rangeIndexFilter.keyEnd=cv;
                rangeIndexFilter.strictEnd=false;
                break;
            case EQUAL:
                rangeIndexFilter.keyStart=rangeIndexFilter.keyEnd=cv;
                rangeIndexFilter.strictStart=rangeIndexFilter.strictEnd=false;
                break;
            case NOT_EQUAL:
                //TODO - two ranges have to be created
            }
            return true;
        } else if((relOp==RelOp.EQUAL) && tableDefinition.hasPartitioning()) {
            PartitioningSpec pspec=tableDefinition.getPartitioningSpec();
            if (cexpr.getName().equals(pspec.valueColumn)) {
                Set<Object> values=new HashSet<Object>();
                values.add(value);
                values=transformEnums(values);
                if(partitionFilter==null) {
                    partitionFilter=values;
                } else {
                    partitionFilter.retainAll(values); 
                }
                return true;
            }
        }
        return false;
    }


    //if the value partitioning column is of type Enum, we have to convert all the values 
    // from String to Short
    // if partitioning value is not an enum, return it unchanged
    private Set<Object> transformEnums(Set<Object> values) {
        PartitioningSpec pspec=tableDefinition.getPartitioningSpec();
        ColumnDefinition cd=tableDefinition.getColumnDefinition(pspec.valueColumn);
        
        if(cd.getType()==DataType.ENUM) { 
            BiMap<String, Short> enumValues=tableDefinition.getEnumValues(pspec.valueColumn);
            Set<Object> v1=new HashSet<Object>();
            for(Object o: values) {
                Object o1=enumValues.get(o);
                if(o1==null) {
                    log.debug("no enum value for column: {} value: {}", pspec.valueColumn, o);
                } else {
                    v1.add(o1);
                }
            }
            values=v1;
        }
        return values;
    }
    /**
     * currently adds only filters on value based partitions
     */
    @Override
    public boolean addInFilter(ColumnExpression cexpr, Set<Object> values) throws StreamSqlException {
        if(!tableDefinition.hasPartitioning()) return false;
        PartitioningSpec pspec=tableDefinition.getPartitioningSpec();
        
        if((pspec.valueColumn==null) || (!pspec.valueColumn.equals(cexpr.getName()))) return false;
        values=transformEnums(values);
        
        if(partitionFilter==null) {
            partitionFilter=values;
        } else {
            partitionFilter.retainAll(values);
        }
        return true;
    }
    
    
    @Override
    public void doClose() {
        quit=true;
    }


    public TableDefinition getTableDefinition() {
        return tableDefinition;
    }
    
    //this is a lexicographic comparison which returns 0 if one of the array is a subarray of the other one
    // it is useful when the filter key is shorter than the index key
    private int compare(byte[] a1, byte[] a2) {
        for(int i=0;i<a1.length && i<a2.length;i++) {
            int d=(a1[i]&0xFF)-(a2[i]&0xFF);
            if(d!=0)return d;
        }
        return 0;
    }

    
    
    class TcRawTuple extends RawTuple {
        int index;//used for sorting tuples with equals keys
        YBDB db;
        YBDBCUR cursor;
        
        public RawTuple(byte[] key, byte[] value, YBDB db, YBDBCUR cursor, int index) {
        	super(key,value, index);
            this.db = db;
            this.cursor = cursor;
            this.index=index;
        }
    }
}
