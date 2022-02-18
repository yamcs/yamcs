package org.yamcs.yarch;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.utils.TimeInterval;

/**
 * Sends histogram data to a stream.
 * 
 * The definition of the emitted tuples is in {@link org.yamcs.yarch.streamsql.TupleSourceExpression#bind}
 * 
 * @author nm
 *
 */
public class HistogramReaderStream extends Stream implements Runnable {
    // this is the table and the column on which we run the histogram
    final private ColumnSerializer<?> histoColumnSerializer;
    HistogramIterator iter;
    final TableDefinition tblDef;
    final String histoColumnName;
    // filter conditions
    TimeInterval timeInterval = new TimeInterval();

    long mergeTime = 2000;

    static AtomicInteger count = new AtomicInteger(0);
    volatile boolean quit = false;
    private ColumnDefinition histoColumnDefinition;

    public HistogramReaderStream(YarchDatabaseInstance ydb, TableDefinition tblDef, String histoColumnName,
            TupleDefinition tupleDef) throws YarchException {
        super(ydb, tblDef.getName() + "_histo_" + count.getAndIncrement(), tupleDef);
        this.histoColumnSerializer = tblDef.getColumnSerializer(histoColumnName);
        this.histoColumnDefinition = tblDef.getColumnDefinition(histoColumnName);
        this.tblDef = tblDef;
        this.histoColumnName = histoColumnName;
    }

    @Override
    public void doStart() {
        (new Thread(this, "HistogramReader[" + getName() + "]")).start();
    }

    @Override
    public void run() {
        if (log.isDebugEnabled()) {
            log.debug("starting a histogram stream for interval {}, mergeTime: {})", timeInterval.toStringEncoded(),
                    mergeTime);
        }
        try {
            iter = ydb.getStorageEngine(tblDef).getHistogramIterator(ydb, tblDef, histoColumnName, timeInterval);
            HistogramRecord r;
            while (!quit && iter.hasNext()) {
                r = iter.next();
                emit(r);
            }
            return;
        } catch (Exception e) {
            log.error("got exception ", e);
        } finally {
            close();
        }
    }

    private void emit(HistogramRecord r) throws IOException {
        Object cvalue = histoColumnSerializer.fromByteArray(r.columnv, histoColumnDefinition);
        Tuple t = new Tuple(getDefinition(), new Object[] { cvalue, r.start, r.stop, r.num });
        emitTuple(t);
    }


    /**
     * Retrieve only the histograms overlapping with this interval.
     * 
     * @param filter
     */
    public void setTimeInterval(TimeInterval filter) {
        this.timeInterval = filter;
    }
    
    public void setMergeTime(long mergeTime) {
        this.mergeTime = mergeTime;
    }

    @Override
    public void doClose() {
        iter.close();
        quit = true;
    }
}
