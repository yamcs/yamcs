package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.ByteArrayWrapper;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.HistogramInfo;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitionManager.Interval;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import org.yamcs.yarch.streamsql.StreamSqlException;

import static org.yamcs.yarch.HistogramSegment.segmentStart;
import static org.yamcs.yarch.rocksdb.RdbHistogramInfo.histoDbKey;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;

/**
 * rebuilds the histogram for a table
 * 
 * @author nm
 *
 */
public class HistogramRebuilder {
    final YarchDatabaseInstance ydb;
    final TableDefinition tableDefinition;
    static Logger log = LoggerFactory.getLogger(HistogramRebuilder.class);
    Tablespace tablespace;
    protected TableVisitor visitor;

    public HistogramRebuilder(Tablespace tablespace, YarchDatabaseInstance ydb, String tableName) {
        this.ydb = ydb;
        this.tablespace = tablespace;
        tableDefinition = ydb.getTable(tableName);
        if (tableDefinition == null) {
            throw new IllegalArgumentException("No table named '" + tableName + "' in instance " + ydb.getName());
        }

        if (!tableDefinition.hasHistogram()) {
            throw new IllegalArgumentException("Table '" + tableName + " does not have histograms");
        }
    }

    public CompletableFuture<Void> rebuild() throws YarchException {
        return rebuild(new TimeInterval());
    }
    public CompletableFuture<Void> rebuild(TimeInterval timeIterval) throws YarchException {
        if (timeIterval.hasStart() || timeIterval.hasEnd()) {
            log.info("Rebuilding histogram for table {}/{} time interval: {}", ydb.getName(), tableDefinition.getName(),
                    timeIterval.toStringEncoded());
        } else {
            log.info("Rebuilding histogram for table {}/{}", ydb.getName(), tableDefinition.getName());
        }
        CompletableFuture<Void> startCf = new CompletableFuture<Void>();

        PartitionManager partitionManager = tablespace.getTable(tableDefinition).getPartitionManager();

        // an Interval is a collection of value based partitions for the same time interval
        // in the rocksdb2 engine all these partitions share the same rocksdb database
        // we need to iterate over such intervals to snapshot the corresponding database while freezing the normal
        // histogram writers
        Iterator<Interval> intervalIterator = partitionManager.intervalIterator(timeIterval);
        CompletableFuture<Void> cf = startCf;

        while (intervalIterator.hasNext()) {
            Interval interval = intervalIterator.next();
            CompletableFuture<Void> cf1 = cf;
            cf = cf1.thenAccept(v -> {
                rebuildHistogramsForInterval(interval, cf1);
            });
        }
        startCf.complete(null);
        return cf;
    }

    private void rebuildHistogramsForInterval(Interval interval, CompletableFuture<Void> cf) {
        HistogramWriter histoWriter = tablespace.getTable(tableDefinition).getHistogramWriter();
        RdbPartition p0 = (RdbPartition) interval.iterator().next();
        try (ExecutionContext ctx = new ExecutionContext(ydb)) {
            ctx.setTablespace(tablespace);
            ctx.addSnapshot(tablespace.getRdb(p0.dir), histoWriter.startQueueing(p0.dir).get());
            if (!deleteHistograms(interval, cf)) {
                return;
            }

            RdbTableWalker tw = new RdbTableWalker(ctx, tableDefinition, true, false);
            try {
                MyTableVisitor visitor = new MyTableVisitor(interval, cf);
                tw.walkInterval(interval, new DbRange(), visitor);
                visitor.flush();
            } catch (YarchException | IOException | RocksDBException | StreamSqlException e1) {
                cf.completeExceptionally(e1);
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            cf.completeExceptionally(e);
        } finally {
            histoWriter.stopQueueing(p0.dir);
        }
    }

    boolean deleteHistograms(Interval interval, CompletableFuture<Void> cf) {
        IntArray a = new IntArray();
        try {
            for (HistogramInfo hi : interval.removeHistograms()) {
                RdbHistogramInfo histo = (RdbHistogramInfo) hi;
                YRDB db = tablespace.getRdb(histo.partitionDir, false);
                db.getDb().deleteRange(dbKey(histo.tbsIndex), dbKey(histo.tbsIndex + 1));
                a.add(((RdbHistogramInfo) hi).tbsIndex);
            }
            tablespace.removeTbsIndices(Type.HISTOGRAM, a);
        } catch (Exception e) {
            log.error("Error when removing existing histograms", e);
            cf.completeExceptionally(e);
            return false;
        }
        return true;
    }

    // "visits" data for one interval
    class MyTableVisitor implements TableVisitor {
        List<ColumnHistoRebuilder> clist = new ArrayList<>();
        final CompletableFuture<Void> cf;
        String partitionDir;

        MyTableVisitor(Interval interval, CompletableFuture<Void> cf) throws IOException {
            this.cf = cf;
            PartitionManager partitionManager = ydb.getPartitionManager(tableDefinition);
            this.partitionDir = ((RdbPartition) interval.iterator().next()).dir;
            for (String columnName : tableDefinition.getHistogramColumns()) {
                RdbHistogramInfo histInfo = (RdbHistogramInfo) partitionManager
                        .createAndGetHistogram(interval.getStart(), columnName);
                clist.add(new ColumnHistoRebuilder(histInfo, columnName));
            }
        }

        @Override
        public Action visit(byte[] key, byte[] value) {
            Tuple tuple = tableDefinition.deserialize(key, value);

            for (ColumnHistoRebuilder chr : clist) {
                try {
                    chr.addTuple(tuple);
                } catch (IOException | RocksDBException e) {
                    cf.completeExceptionally(e);
                    return ACTION_STOP;
                }
            }
            return ACTION_CONTINUE;
        }

        void flush() throws IOException, RocksDBException {
            for (ColumnHistoRebuilder chr : clist) {
                chr.flush();
            }
        }

        // builds histograms for one column
        class ColumnHistoRebuilder {
            String columnName;
            int MAX_ENTRIES = 100;
            RdbHistogramInfo histoInfo;

            ColumnHistoRebuilder(RdbHistogramInfo histoInfo, String columnName) {
                this.columnName = columnName;
                this.histoInfo = histoInfo;
                if (histoInfo == null) {
                    throw new NullPointerException();
                }
            }

            // we know the data will be sorted so we need to maintain for each column value just the last segment.
            // As soon as a data point falling in the next segment comes, we write out the segment to the database and
            // start a new one
            Map<ByteArrayWrapper, HistogramSegment> values = new HashMap<>();

            void addTuple(Tuple tuple) throws IOException, RocksDBException {
                long time = (Long) tuple.getColumn(0);

                ColumnSerializer cs = tableDefinition.getColumnSerializer(columnName);
                byte[] columnv = cs.toByteArray(tuple.getColumn(columnName));

                long sstart = segmentStart(time);
                int dtime = (int) (time % HistogramSegment.GROUPING_FACTOR);

                ByteArrayWrapper valuew = new ByteArrayWrapper(columnv);

                HistogramSegment segment = values.get(valuew);
                if (segment == null) {
                    segment = new HistogramSegment(columnv, sstart);
                    values.put(valuew, segment);
                } else if (segment.getSegmentStart() != sstart) {
                    // write to db
                    YRDB rdb = tablespace.getRdb(partitionDir, false);
                    byte[] dbKey = histoDbKey(histoInfo.tbsIndex, segment.getSegmentStart(), columnv);
                    rdb.put(dbKey, segment.val());

                    segment = new HistogramSegment(columnv, sstart);
                    values.put(valuew, segment);
                }
                segment.merge(dtime);
            }

            void flush() throws IOException, RocksDBException {
                YRDB rdb = tablespace.getRdb(partitionDir, false);
                for (Map.Entry<ByteArrayWrapper, HistogramSegment> me : values.entrySet()) {
                    HistogramSegment segment = me.getValue();
                    byte[] columnv = me.getKey().getData();

                    byte[] dbKey = histoDbKey(histoInfo.tbsIndex, segment.getSegmentStart(), columnv);
                    rdb.put(dbKey, segment.val());
                }
            }
        }
    }
}
