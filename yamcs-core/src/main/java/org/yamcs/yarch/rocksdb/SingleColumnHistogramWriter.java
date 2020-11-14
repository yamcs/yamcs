package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.rocksdb.RocksDBException;
import org.rocksdb.Snapshot;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.Row;

/**
 * Histogram writer for the tables having one column histogram (which is actually all standard tables from Yamcs)
 * 
 * @author nm
 *
 */
public class SingleColumnHistogramWriter extends HistogramWriter {

    final ColumnHistogramWriter colHistoWriter;
    final Map<String, WhileRebuild> wrs = new HashMap<>();
    long lastCleanupTime;

    public SingleColumnHistogramWriter(RdbTable table, String histoColumn) {
        super(table);
        this.colHistoWriter = new ColumnHistogramWriter(histoColumn);
    }

    boolean printed = false;

    @Override
    public synchronized void addHistogram(Row row) throws IOException, RocksDBException {
        String columnName = colHistoWriter.columnName;
        Object colValue = row.get(columnName);
        if (colValue == null) {
            return;
        }

        long time = (Long) row.get(0);
        ColumnSerializer cs = tableDefinition.getColumnSerializer(columnName);
        byte[] v = cs.toByteArray(row.get(columnName));
        RdbHistogramInfo histo = table.createAndGetHistogram(time, columnName);
        WhileRebuild wr = wrs.get(histo.partitionDir);
        if (wr == null) {
            colHistoWriter.addHistogram(time, v);
        } else {
            if (wr.cf != null) {
                // the histogram rebuilder is waiting for a snapshot
                sendSnapshot(histo.partitionDir, wr);

                // we know that this tuple has already been added to the db so it is part of the snapshot
                // we therefore don't queue this tuple but start from the next one
            } else {
                wr.queue.add(new HistoData(time, v));
            }
        }
    }

    /**
     * return a completable future which returns a snapshot after which the histogram data is being queued, such that
     * the snapshot+queued histogram data represents accurately the state of the table.
     * <p>
     * The queue stores only the histogram data, the data itself (table records) is written to the database by the table
     * writer (we definitely do not want to block that!).
     * 
     * <p>
     * The reason we don't create directly the snapshot is to avoid race conditions if there is a fast writer which may
     * have already written the data and just waiting to add the histogram. Creating the snapshot in the writer
     * thread avoid the data being counted twice.
     * <p>
     * Unfortunately this is still not 100% safe if there are two threads writing in the table:
     * 
     * <pre>
     * t1 thread 0: start histogram rebuild, wait to get a snapshot
     * t2 thread 1: write a record to table
     * t3 thread 2: write a record to table
     * t4 thread 1: take snapshot and enable queueing
     * t5 thread 2: add the record to the histogram queue
     * t6 thread 0: rebuild the histograms based on the snapshot
     * t7 thread 0: stop queueing, add the queued data to the histograms. 
     *  The data added in the queue at step t5 will be counted twice because it was already part of the snapshot.
     * </pre>
     * 
     * <p>
     * If there is no table writer, the rebuilder will wait forever for the snapshot so to avoid this we terminate the
     * future after a few milliseconds. This too can induce a race condition.
     * 
     * <p>
     * To avoid those race conditions we would need from rocksdb the sequence number for each write to be able to
     * compare them with the snapshot sequence number and thus know if the data has already been written.
     * <p>
     * An alternative would be to synchronise all the writers.
     * <p>
     * However, given the fact that histogram rebuild is an infrequent operation and most tables will only have maximum
     * one
     * steady writer (this works correctly), the problem is unlikely to appear in practice. In addition, the histograms
     * being statistical in nature, having a counter off by one is not considered to be a major problem.
     * 
     */
    @Override
    public synchronized CompletableFuture<Snapshot> startQueueing(String dbPartition) throws IOException {
        if (wrs.containsKey(dbPartition)) {
            throw new IllegalStateException("Already queing for this partition");
        }
        WhileRebuild wr = new WhileRebuild();
        wrs.put(dbPartition, wr);
        tablespace.getExecutor().schedule(() -> {
            try {
                sendSnapshot(dbPartition, wr);
            } catch (IOException e) {
                wr.cf.completeExceptionally(e);
            }
        }, 100, TimeUnit.MILLISECONDS);
        return wr.cf;
    }

    private synchronized void sendSnapshot(String partitionDir, WhileRebuild wr) throws IOException {
        if (wr.cf == null || wr.cf.isDone()) {
            return;
        }
        YRDB rdb = tablespace.getRdb(partitionDir);
        wr.cf.complete(rdb.getDb().getSnapshot());
        wr.cf = null;
    }

    public synchronized void stopQueueing(String dbPartition) {
        WhileRebuild wr = wrs.remove(dbPartition);
        if (wr == null) {
            throw new IllegalStateException("Not queing for this partition");
        }
        for (HistoData hd : wr.queue) {
            colHistoWriter.addHistogram(hd.time, hd.value);
        }
    }

    static class HistoData {
        long time;
        byte[] value;

        public HistoData(long time, byte[] value) {
            this.time = time;
            this.value = value;
        }
    }

    static class WhileRebuild {
        CompletableFuture<Snapshot> cf = new CompletableFuture<>();
        ArrayList<HistoData> queue = new ArrayList<>();
    }
}
