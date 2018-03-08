package org.yamcs.yarch.oldrocksdb;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * rebuilds the histogram for a table
 * 
 * @author nm
 *
 */
public class HistogramRebuilder {
    final YarchDatabaseInstance ydb;
    final TableDefinition tblDef;
    private static AtomicInteger streamCounter = new AtomicInteger();
    static Logger log = LoggerFactory.getLogger(HistogramRebuilder.class);

    public HistogramRebuilder(YarchDatabaseInstance ydb, String tableName) {
        this.ydb = ydb;
        tblDef = ydb.getTable(tableName);
        if (tblDef == null) {
            throw new IllegalArgumentException("No table named '" + tableName + "' in instance " + ydb.getName());
        }

        if (!tblDef.hasHistogram()) {
            throw new IllegalArgumentException("Table '" + tableName + " does not have histograms");
        }
    }

    public CompletableFuture<Void> rebuild(TimeInterval interval) throws YarchException {
        if (interval.hasStart() || interval.hasEnd()) {
            log.info("Rebuilding histogram for table {}/{} time interval: {}", ydb.getName(), tblDef.getName(),
                    interval.toStringEncoded());
        } else {
            log.info("Rebuilding histogram for table {}/{}", ydb.getName(), tblDef.getName());
        }

        CompletableFuture<Void> cf = new CompletableFuture<Void>();

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
        AbstractTableWriter tw = (AbstractTableWriter) rdbsr.newTableWriter(ydb, tblDef, InsertMode.INSERT);
        try {
            ydb.execute("create stream " + streamName + " as select * from " + tblDef.getName()
                    + getWhereCondition(timeColumnName, interval));
        } catch (StreamSqlException | ParseException e) {
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
                    YRDB db = rdbFactory.getRdb(tblDef.getDataDir() + "/" + partition.dir, false);
                    tw.addHistogram(db, tuple);
                } catch (Exception e) {
                    cf.completeExceptionally(e);
                }
            }
        });
        stream.start();

        return cf;
    }

    private String getWhereCondition(String timeColumnName, TimeInterval interval) {
        if (!interval.hasStart() && !interval.hasEnd()) {
            return "";
        }
        StringBuilder whereCnd = new StringBuilder();
        whereCnd.append(" where ");
        if (interval.hasStart()) {
            long start = HistogramSegment.GROUPING_FACTOR * (interval.getStart() / HistogramSegment.GROUPING_FACTOR);
            whereCnd.append(timeColumnName + " >= " + start);
            if (interval.hasEnd()) {
                whereCnd.append(" and ");
            }
        }
        if (interval.hasEnd()) {
            long stop = HistogramSegment.GROUPING_FACTOR * (1 + interval.getEnd() / HistogramSegment.GROUPING_FACTOR);
            whereCnd.append(timeColumnName + " < " + stop);
        }

        return whereCnd.toString();
    }

    void deleteHistograms(TimeInterval interval) throws RocksDBException, IOException {
        RdbStorageEngine rdbsr = RdbStorageEngine.getInstance();
        RDBFactory rdbf = RDBFactory.getInstance(ydb.getName());
        Iterator<List<Partition>> partitionIterator;
        if (interval.hasStart()) {
            partitionIterator = rdbsr.getPartitionManager(tblDef).iterator(interval.getStart(), null);
        } else {
            partitionIterator = rdbsr.getPartitionManager(tblDef).iterator(null);
        }

        byte[] empty = new byte[0];

        while (partitionIterator.hasNext()) {
            RdbPartition p0 = (RdbPartition) partitionIterator.next().get(0);
            if (interval.hasEnd() && p0.getStart() > interval.getEnd()) {
                break;
            }
            log.debug("Removing existing histogram for partition {}", p0.dir);

            long pstart = p0.getStart() / HistogramSegment.GROUPING_FACTOR;
            long pend = p0.getEnd() / HistogramSegment.GROUPING_FACTOR;

            long kstart = interval.hasStart() ? Math.max(interval.getStart() / HistogramSegment.GROUPING_FACTOR, pstart)
                    : pstart;
            long kend = interval.hasEnd() ? Math.min(interval.getEnd() / HistogramSegment.GROUPING_FACTOR, pend) : pend;

            YRDB rdb = rdbf.getRdb(tblDef.getDataDir() + "/" + p0.dir, false);
            for (String colName : tblDef.getHistogramColumns()) {
                String cfHistoName = AbstractTableWriter.getHistogramColumnFamilyName(colName);
                ColumnFamilyHandle cfh = rdb.getColumnFamilyHandle(cfHistoName);
                if (cfh == null) {
                    log.debug("No existing histogram column family for {}", colName);
                    continue;
                }
                if ((kstart == pstart) && (kend == pend)) {
                    log.debug("Dropping column family {}", colName);
                    rdb.dropColumnFamily(cfh);
                } else {
                    try (WriteBatch writeBatch = new WriteBatch();
                            RocksIterator it = rdb.getDb().newIterator(cfh);
                            WriteOptions wo = new WriteOptions()) {
                        it.seek(HistogramSegment.key(kstart, empty));
                        while (it.isValid()) {
                            long k = HistogramSegment.getSstart(it.key());
                            if (k > kend) {
                                break;
                            }
                            writeBatch.remove(cfh, it.key());
                            it.next();
                        }
                        rdb.getDb().write(wo, writeBatch);
                    }
                }
            }
        }

    }
}
