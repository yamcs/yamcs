package org.yamcs.yarch.rocksdb;

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
import org.yamcs.yarch.HistogramEncoder;
import org.yamcs.yarch.HistogramEncoder.SplitTimeResult;
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
        final HistogramEncoder encoder;
        int MAX_ENTRIES = 100;

        private LinkedHashMap<ByteArrayWrapper, HistogramSegment> segments = new LinkedHashMap<ByteArrayWrapper, HistogramSegment>() {
            protected boolean removeEldestEntry(Map.Entry<ByteArrayWrapper, HistogramSegment> eldest) {
                return size() > MAX_ENTRIES;
            };
        };

        public ColumnHistogramWriter(String columnName) {
            this.columnName = columnName;
            this.encoder = HistogramEncoder.createEncoder(tableDefinition);
        }

        void addHistogram(long time, byte[] value) {
            RdbHistogramInfo histo;
            try {
                long segmentStartTime = encoder.segmentStart(time);
                histo = (RdbHistogramInfo) table.createAndGetHistogram(segmentStartTime, columnName);
                YRDB rdb = tablespace.getRdb(histo.partitionDir, false);

                SplitTimeResult splitResult = encoder.splitTime(time);
                byte[] histoDbKey = encoder.encodeKey(histo.tbsIndex, splitResult.segIndex(), value);
                ByteArrayWrapper hmkey = new ByteArrayWrapper(histoDbKey);
                
                HistogramSegment segment = segments.get(hmkey);
                if (segment == null) {
                    byte[] val = rdb.get(histoDbKey);
                    if (val == null) {
                        segment = new HistogramSegment(value, splitResult.segIndex());
                    } else {
                        List<HistogramSegment.SegRecord> decodedRecords = encoder.decodeValue(val, splitResult.segIndex());
                        segment = new HistogramSegment(value, splitResult.segIndex(), decodedRecords);
                    }
                }
                segment.merge(splitResult.deltaTime());
                byte[] encodedValue = encoder.encodeValue(segment);
                rdb.put(histoDbKey, encodedValue);

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
