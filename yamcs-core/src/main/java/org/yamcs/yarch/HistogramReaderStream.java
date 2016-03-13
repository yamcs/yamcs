package org.yamcs.yarch;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.TimeInterval;
import org.yamcs.yarch.HistogramDb.HistogramIterator;
import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.RelOp;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

/**
 * Sends histogram data to a stream.
 * 
 * The definition of the emitted tuples is in {@link org.yamcs.yarch.streamsql.TupleSourceExpression#bind}
 * 
 * @author nm
 *
 */
public class HistogramReaderStream extends AbstractStream implements Runnable, DbReaderStream {
    //this is the table and the column on which we run the histogram
    final private ColumnSerializer histoColumnSerializer;
    HistogramDb histoDb;
    
    
    //filter conditions
    TimeInterval interval = new TimeInterval();

    long mergeTime=2000;

    static AtomicInteger count=new AtomicInteger(0);
    volatile boolean quit=false;

    public HistogramReaderStream(YarchDatabase ydb, TableDefinition tblDef, String histoColumnName, TupleDefinition tupleDef) throws YarchException {
        super(ydb, tblDef.getName()+"_histo_"+count.getAndIncrement(), tupleDef);
        this.histoColumnSerializer=tblDef.getColumnSerializer(histoColumnName);
        
        this.histoDb = ydb.getStorageEngine(tblDef).getHistogramDb(tblDef);
    }

    @Override 
    public void start() {
        (new Thread(this, "HistogramReader["+getName()+"]")).start();
    }

    @Override
    public void run() {
        log.debug("starting a histogram stream for interval {}, mergeTime: {})", interval, mergeTime);

        try {
            HistogramIterator iter=histoDb.getIterator(histoColumnSerializer.getColumnName(), interval, mergeTime);
            HistogramRecord r;
            while (!quit && (r=iter.getNextRecord())!=null) {
                emit(r);
            }
            return;
        } catch (Exception e) {
            log.error("got exception ", e);
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void emit(HistogramRecord r) throws IOException {
        Object cvalue=histoColumnSerializer.fromByteArray(r.columnv);
        Tuple t=new Tuple(getDefinition(), new Object[]{cvalue, r.start, r.stop, r.num});
        emitTuple(t);
    }

    /* puts conditions on the first or last. doesn't work properly yet TODO*/
    @Override
    public boolean addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException {
        String cname=cexpr.getName();
        if("first".equals(cname) || "last".equals(cname)) {
            long time;
            try {
                time= (Long) DataType.castAs(DataType.TIMESTAMP, value);
            } catch (IllegalArgumentException e){
                throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
            }
            switch(relOp) {
            case GREATER:
            case GREATER_OR_EQUAL:
                interval.setStart(time);
                return true;
            case LESS:
            case LESS_OR_EQUAL:
                interval.setStop(time);
                return true;
            case EQUAL:
                interval.setStart(time);
                interval.setStop(time);
                return true;
            case NOT_EQUAL:
                //TODO - two ranges have to be created
            }
        }
        return false;
    }

    /**
     * could filter on the histoColumn values, not done for the moment
     */
    @Override
    public boolean addInFilter(ColumnExpression cexpr, Set<Object> values) {
        return false;
    }

    public void setMergeTime(long mergeTime) {
        this.mergeTime=mergeTime;
    }
    
    @Override
    public void doClose() {
        quit=true;
    }
}
