package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.HistogramInfo;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitionManager.Interval;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;
/**
 * rebuilds the histogram for a table
 * @author nm
 *
 */
public class HistogramRebuilder {
    final YarchDatabaseInstance ydb;
    final TableDefinition tblDef;
    private static AtomicInteger streamCounter = new AtomicInteger();
    static Logger log = LoggerFactory.getLogger(HistogramRebuilder.class);
    Tablespace tablespace;
    
    public HistogramRebuilder(Tablespace tablespace, YarchDatabaseInstance ydb, String tableName) {
        this.ydb = ydb;
        this.tablespace = tablespace;
        tblDef = ydb.getTable(tableName);
        if(tblDef==null) {
            throw new IllegalArgumentException("No table named '"+tableName+"' in instance "+ydb.getName());
        }

        if(!tblDef.hasHistogram()) {
            throw new IllegalArgumentException("Table '"+tableName+" does not have histograms");
        }
    }

    public CompletableFuture<Void> rebuild(TimeInterval interval) throws YarchException {
        if(interval.hasStart()||interval.hasEnd()) {
            log.info("Rebuilding histogram for table {}/{} time interval: {}", ydb.getName(), tblDef.getName(), interval.toStringEncoded());
        } else {
            log.info("Rebuilding histogram for table {}/{}", ydb.getName(), tblDef.getName());
        }

        CompletableFuture<Void> cf = new  CompletableFuture<Void>();

        try {
            deleteHistograms(interval);
        } catch (Exception e) {
            log.error("Error when removing existing histograms", e);
            cf.completeExceptionally(e);
            return cf;
        }
        
        String timeColumnName = tblDef.getTupleDefinition().getColumn(0).getName();
        String streamName = "histo_rebuild_" + streamCounter.incrementAndGet();
        
        RdbStorageEngine rdbsr = RdbStorageEngine.getInstance();
        RdbTableWriter tw = (RdbTableWriter) rdbsr.newTableWriter(ydb, tblDef, InsertMode.INSERT); 
        try {
            ydb.execute("create stream "+streamName+" as select * from "+tblDef.getName() + getWhereCondition(timeColumnName, interval));
        } catch (StreamSqlException|ParseException e) {
            throw new RuntimeException(e);
        }
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());

        Stream stream = ydb.getStream(streamName);
        stream.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                cf.complete(null);
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                try {
                    RdbPartition partition = tw.getDbPartition(tuple);
                    YRDB db = tablespace.getRdb(partition.dir, false);
                    tw.addHistogram(db, tuple);
                } catch (Exception e) {
                    cf.completeExceptionally(e);
                }
            }
        });
        stream.start();

        return cf;
    }

    public static String getWhereCondition(String timeColumnName,  TimeInterval interval) {
        if(!interval.hasStart() && !interval.hasEnd()){
            return "";
        }
        StringBuilder whereCnd = new StringBuilder();
        whereCnd.append(" where ");
        if(interval.hasStart()) {
            long start = HistogramSegment.GROUPING_FACTOR*(interval.getStart()/HistogramSegment.GROUPING_FACTOR);
            whereCnd.append(timeColumnName+" >= "+ start);
            if(interval.hasEnd()) {
                whereCnd.append(" and ");
            }
        }
        if(interval.hasEnd()) {
            long stop = HistogramSegment.GROUPING_FACTOR*(1+interval.getEnd()/HistogramSegment.GROUPING_FACTOR);
            whereCnd.append(timeColumnName+" < "+ stop);
        }
        
        return whereCnd.toString();
    }


    void deleteHistograms(TimeInterval timeInterval) throws RocksDBException, IOException {
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        PartitionManager partMgr = rse.getPartitionManager(tblDef);
        IntArray a = new IntArray();
        Iterator<Interval> intervalIterator = partMgr.intervalIterator(timeInterval);
        
        while(intervalIterator.hasNext()) {
            Interval intv = intervalIterator.next();
            for(HistogramInfo hi: intv.removeHistograms()) {
                RdbHistogramInfo histo = (RdbHistogramInfo)hi;
                YRDB db = tablespace.getRdb(histo.partitionDir, false);
                db.getDb().deleteRange(dbKey(histo.tbsIndex), dbKey(histo.tbsIndex + 1));
                a.add(((RdbHistogramInfo)hi).tbsIndex);
            }
        }
        tablespace.removeTbsIndices(Type.HISTOGRAM, a);
    }
}
