package org.yamcs.yarch.rocksdb;

import static org.yamcs.yarch.HistogramSegment.segmentStart;
import static org.yamcs.yarch.rocksdb.RdbHistogramInfo.histoDbKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.rocksdb.RocksDBException;
import org.rocksdb.Snapshot;
import org.yamcs.utils.ByteArrayWrapper;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.Row;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchException;

/**
 * Writes histograms for one table.
 * <p>
 * There is one of these objects for each table used by the table writers.
 * <p>
 * The {@link HistogramRebuilder} will use another writer during build
 * 
 * <p>
 * It does allow concurrent access
 * 
 * @author nm
 *
 */
public abstract class HistogramWriter {
    static final int CLEANUP_INTERVAL = 60_000;
    final protected Tablespace tablespace;
    final protected TableDefinition tableDefinition;
    final protected RdbTable table;
    final protected List<ColumnHistogramWriter> columnWriters = new ArrayList<>();

    public HistogramWriter(RdbTable table) {
        this.table = table;
        this.tableDefinition =  table.getDefinition();
        this.tablespace = table.getTablespace();

    }

    public abstract void addHistogram(Row sertuple) throws IOException, RocksDBException;

    /**
     * called from the histogram rebuilder to start queueing all new data while the builder rebuilds a (part) of the.
     * <p>
     * Returns a snapshot of the database for the given partition at the time of the call.
     * <p>
     * The caller is responsible for releasing the snapshot 
     * histograms
     * @param dir 
     * @throws IOException 
     */
    public abstract CompletableFuture<Snapshot> startQueueing(String dir) throws IOException;

    /**
     * called from the histogram rebuilder to stop queuing and start again updating histograms starting with the ones
     * queued
     * @param partitionDir 
     */
    public abstract void stopQueueing(String partitionDir);

    
    public static HistogramWriter newWriter(RdbTable table) {
        TableDefinition tblDef = table.getDefinition();
        List<String> histoColumns = tblDef.getHistogramColumns();
        if (histoColumns == null || histoColumns.isEmpty()) {
            return null;
        }
        if (histoColumns.size() == 1) {
            return new SingleColumnHistogramWriter(table, histoColumns.get(0));
        } else {
            throw new UnsupportedOperationException("multi column histograms not implemented yet");
        }
    }
    /**
     * handles histogram writes for one column.
     * <p>
     * Keeps a cache of recent modified histogram segments to avoid retrieving them from the db each time.
     * 
     */
    class ColumnHistogramWriter {
        final String columnName;
        int MAX_ENTRIES = 100;

        private LinkedHashMap<ByteArrayWrapper, HistogramSegment> segments = new LinkedHashMap<ByteArrayWrapper, HistogramSegment>() {
            protected boolean removeEldestEntry(Map.Entry<ByteArrayWrapper, HistogramSegment> eldest) {
                return size() > MAX_ENTRIES;
            };
        };

        public ColumnHistogramWriter(String columnName) {
            this.columnName = columnName;
        }

        void addHistogram(long time, byte[] value) {
            RdbHistogramInfo histo;
            try {
                histo = (RdbHistogramInfo) table.createAndGetHistogram(time, columnName);
                YRDB rdb = tablespace.getRdb(histo.partitionDir, false);

                long sstart = segmentStart(time);
                int dtime = (int) (time % HistogramSegment.GROUPING_FACTOR);
                byte[] histoDbKey = histoDbKey(histo.tbsIndex, sstart, value);
                ByteArrayWrapper hmkey = new ByteArrayWrapper(histoDbKey);
                
                HistogramSegment segment = segments.get(hmkey);
                if (segment == null) {
                    byte[] val = rdb.get(histoDbKey);
                    if (val == null) {
                        segment = new HistogramSegment(value, sstart);
                    } else {
                        segment = new HistogramSegment(value, sstart, val);
                    }
                }
                segment.merge(dtime);
                rdb.put(histoDbKey, segment.val());

                segments.put(hmkey, segment);
            } catch (RocksDBException e) {
                throw new YarchException(e);
            }
        }

        public void cleanup() {
            segments.clear();
        }
    }
}
