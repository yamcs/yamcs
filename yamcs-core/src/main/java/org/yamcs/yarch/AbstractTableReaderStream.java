package org.yamcs.yarch;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.RelOp;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;
import com.google.common.primitives.UnsignedBytes;

/**
 * Implements skeleton for table streamer that uses PartitionManager to handle partitioning.
 * 
 * 
 * @author nm
 *
 */
public abstract class AbstractTableReaderStream extends AbstractStream implements Runnable, DbReaderStream {
    protected TableDefinition tableDefinition;
    private IndexFilter rangeIndexFilter; //if not null, the replay should run in this range
    private Set<Object> partitionValueFilter;//if not null, the replay only includes data from these partitions - if the table is partitioned on a non index column

    static AtomicInteger count=new AtomicInteger(0);
    volatile protected boolean quit=false;
    Comparator<byte[]> bytesComparator=UnsignedBytes.lexicographicalComparator();
    private Tuple lastEmitted;

    final protected PartitionManager partitionManager;
    final protected boolean ascending;
    final protected boolean follow;

    protected AbstractTableReaderStream(YarchDatabase ydb, TableDefinition tblDef, PartitionManager partitionManager, boolean ascending, boolean follow) {
        super(ydb, tblDef.getName()+"_"+count.getAndIncrement(), tblDef.getTupleDefinition());
        this.tableDefinition = tblDef;
        this.partitionManager = partitionManager;
        this.ascending = ascending;
        this.follow = follow;
    }


    @Override 
    public void start() {
        (new Thread(this, "TcTableReader["+getName()+"]")).start();
    }

    @Override
    public void run() {
        log.debug("starting a table stream from table {} with rangeIndexFilter {} \n partitionFilter: {}",
                tableDefinition.getName(), rangeIndexFilter, partitionValueFilter);

        try {         
            Iterator<List<Partition>> partitionIterator;

            PartitioningSpec pspec=tableDefinition.getPartitioningSpec();
            if(pspec.valueColumn!=null) {
                if((ascending) && (rangeIndexFilter!=null) && (rangeIndexFilter.keyStart!=null)) {
                    long start=(Long)rangeIndexFilter.keyStart;
                    partitionIterator = partitionManager.iterator(start, partitionValueFilter);
                } else if((!ascending) && (rangeIndexFilter!=null) && (rangeIndexFilter.keyEnd!=null)) {
                    long start=(Long)rangeIndexFilter.keyEnd;
                    partitionIterator = partitionManager.reverseIterator(start, partitionValueFilter);
                } else {
                    if (ascending) {
                        partitionIterator = partitionManager.iterator(partitionValueFilter);
                    } else {
                        partitionIterator = partitionManager.reverseIterator(partitionValueFilter);
                    }
                }
            } else {
                if (ascending) {
                    partitionIterator = partitionManager.iterator(partitionValueFilter);
                } else {
                    partitionIterator = partitionManager.reverseIterator(partitionValueFilter);
                }
            }
            
            while((!quit) && partitionIterator.hasNext()) {
                List<Partition> partitions=partitionIterator.next();
                boolean endReached=runPartitions(partitions, rangeIndexFilter);
                if(endReached) break;
            }            
        } catch (Exception e) {
            log.error("got exception ", e);
            e.printStackTrace();
        } finally {
            close();
        }
    }

    /**
     * Runs the partitions sending data only that conform with the start and end filters. 
     * returns true if the stop condition is met
     * 
     * All the partitions are from the same time interval
     */
    protected abstract boolean runPartitions(List<Partition> partitions, IndexFilter range) throws IOException;

    protected boolean emitIfNotPastStop (RawTuple rt,  byte[] rangeEnd, boolean strictEnd) {
        boolean emit=true;
        if(rangeEnd!=null) { //check if we have reached the end
            int c=compare(rt.getKey(), rangeEnd);
            if(c<0) emit=true;
            else if((c==0) && (!strictEnd)) emit=true;
            else emit=false;
        }
        lastEmitted = dataToTuple(rt.getKey(), rt.getValue());
        if(emit) emitTuple(lastEmitted);
        return emit;
    }

    protected boolean emitIfNotPastStart (RawTuple rt,  byte[] rangeStart, boolean strictStart) {
        boolean emit=true;
        if(rangeStart!=null) { //check if we have reached the start
            int c = compare(rt.getKey(), rangeStart);
            if(c>0) emit=true;
            else if((c==0) && (!strictStart)) emit=true;
            else emit=false;
        }
        lastEmitted = dataToTuple(rt.getKey(), rt.getValue());
        if(emit) emitTuple(lastEmitted);
        return emit;
    }
    
    
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException {
        if(tableDefinition.isIndexedByKey(cexpr.getName())) {
            ColumnDefinition cdef = tableDefinition.getColumnDefinition(cexpr.getName());
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
            PartitioningSpec pspec = tableDefinition.getPartitioningSpec();
            if (cexpr.getName().equals(pspec.valueColumn)) {
                Set<Object> values=new HashSet<Object>();
                values.add(value);
                values = transformEnums(values);
                if(partitionValueFilter==null) {
                    partitionValueFilter=values;
                } else {
                    partitionValueFilter.retainAll(values); 
                }
                return true;
            }
        }
        return false;
    }


    //if the value partitioning column is of type Enum, we have to convert all the values 
    // from String to Short - the values that do not have an enum are eliminated

    // if partitioning value is not an enum, return it unchanged
    private Set<Object> transformEnums(Set<Object> values) {
        PartitioningSpec pspec=tableDefinition.getPartitioningSpec();
        ColumnDefinition cd=tableDefinition.getColumnDefinition(pspec.valueColumn);

        if(cd.getType()==DataType.ENUM) { 
            BiMap<String, Short> enumValues = tableDefinition.getEnumValues(pspec.valueColumn);

            Set<Object> v1=new HashSet<Object>();
            if(enumValues!=null) { //else there is no value in the table yet
                for(Object o: values) {
                    Object o1 = enumValues.get(o);
                    if(o1==null) {
                        log.debug("no enum value for column: {} value: {}", pspec.valueColumn, o);
                    } else {
                        v1.add(o1);
                    }
                }
            }
            values=v1;
        }
        return values;
    }
    protected Tuple dataToTuple(byte[] k, byte[] v) {
        return tableDefinition.deserialize(k, v); //TODO adapt to the stream schema
    }
    /**
     * currently adds only filters on value based partitions
     */
    @Override
    public boolean addInFilter(ColumnExpression cexpr, Set<Object> values) throws StreamSqlException {
        if(!tableDefinition.hasPartitioning()) return false;
        PartitioningSpec pspec=tableDefinition.getPartitioningSpec();

        if((pspec.valueColumn==null) || (!pspec.valueColumn.equals(cexpr.getName()))) return false;
        values = transformEnums(values);

        if(partitionValueFilter==null) {
            partitionValueFilter=values;
        } else {
            partitionValueFilter.retainAll(values);
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
    protected int compare(byte[] a1, byte[] a2) {
        for(int i=0;i<a1.length && i<a2.length;i++) {
            int d=(a1[i]&0xFF)-(a2[i]&0xFF);
            if(d!=0)return d;
        }
        return 0;
    }
}
