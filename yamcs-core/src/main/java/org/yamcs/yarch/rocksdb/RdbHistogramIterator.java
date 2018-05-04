package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import static org.yamcs.yarch.HistogramSegment.segmentStart;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

/**
 * 
 * @author nm
 *
 */
public class RdbHistogramIterator implements HistogramIterator {

    private Iterator<PartitionManager.Interval> intervalIterator;
    private AscendingRangeIterator segmentIterator;

    private PriorityQueue<HistogramRecord> records = new PriorityQueue<>();

    private final TimeInterval interval;
    private final long mergeTime;
    private final Tablespace tablespace;

    YarchDatabaseInstance ydb;
    TableDefinition tblDef;
    YRDB rdb;

    Logger log;
    String colName;
    boolean stopReached = false;

    // FIXME: mergeTime does not merge records across partitions or segments
    public RdbHistogramIterator(Tablespace tablespace, YarchDatabaseInstance ydb, TableDefinition tblDef,
            String colName, TimeInterval interval, long mergeTime) throws RocksDBException, IOException {
        this.interval = interval;
        this.mergeTime = mergeTime;
        this.ydb = ydb;
        this.tblDef = tblDef;
        this.colName = colName;
        this.tablespace = tablespace;

        PartitionManager partMgr = RdbStorageEngine.getInstance().getPartitionManager(tblDef);
        intervalIterator = partMgr.intervalIterator(interval);
        log = LoggingUtils.getLogger(this.getClass(), ydb.getName(), tblDef);
        readNextPartition();
    }

    private void readNextPartition() throws RocksDBException, IOException {
        if (!intervalIterator.hasNext()) {
            stopReached = true;
            return;
        }

        PartitionManager.Interval intv = intervalIterator.next();

        if (rdb != null) {
            tablespace.dispose(rdb);
        }

        RdbHistogramInfo hist = (RdbHistogramInfo) intv.getHistogram(colName);

        if (hist == null) {
            readNextPartition();
            return;
        }
        rdb = tablespace.getRdb(hist.partitionDir, false);

        long segStart = interval.hasStart() ? segmentStart(interval.getStart()) : 0;
        byte[] dbKeyStart = ByteArrayUtils.encodeInt(hist.tbsIndex, new byte[12], 0);
        ByteArrayUtils.encodeLong(segStart, dbKeyStart, TBS_INDEX_SIZE);

        boolean strictEnd;
        byte[] dbKeyStop;
        if (interval.hasEnd()) {
            strictEnd = false;
            dbKeyStop = ByteArrayUtils.encodeInt(hist.tbsIndex, new byte[12], 0);
            long segStop = segmentStart(interval.getEnd());
            ByteArrayUtils.encodeLong(segStop, dbKeyStop, TBS_INDEX_SIZE);
        } else {
            dbKeyStop = ByteArrayUtils.encodeInt(hist.tbsIndex + 1, new byte[12], 0);
            strictEnd = true;
        }
        if (segmentIterator != null) {
            segmentIterator.close();
        }

        segmentIterator = new AscendingRangeIterator(rdb.newIterator(), dbKeyStart, false, dbKeyStop, strictEnd);
        readNextSegments();

    }

    // reads all the segments with the same sstart time
    private void readNextSegments() throws RocksDBException, IOException {
        if (!segmentIterator.isValid()) {
            readNextPartition();
            return;
        }

        ByteBuffer bb = ByteBuffer.wrap(segmentIterator.key());
        long sstart = bb.getLong(RdbStorageEngine.TBS_INDEX_SIZE);

        while (true) {
            boolean beyondStop = addRecords(segmentIterator.key(), segmentIterator.value());
            if (beyondStop) {
                stopReached = true;
            }

            segmentIterator.next();
            if (!segmentIterator.isValid()) {
                readNextPartition();
                break;
            }
            bb = ByteBuffer.wrap(segmentIterator.key());
            long g = bb.getLong();
            if (g != sstart) {
                break;
            }
        }
    }

    public void close() {
        if (rdb != null) {
            if (segmentIterator != null) {
                segmentIterator.close();
                segmentIterator = null;
            }
            tablespace.dispose(rdb);
            rdb = null;
        }
    }

    // add all records from this segment into the queue
    // if the stop has been reached add only partially the records, return true
    private boolean addRecords(byte[] key, byte[] val) {
        ByteBuffer kbb = ByteBuffer.wrap(key, TBS_INDEX_SIZE, key.length - TBS_INDEX_SIZE);
        long sstart = kbb.getLong();
        byte[] columnv = new byte[kbb.remaining()];
        kbb.get(columnv);
        ByteBuffer vbb = ByteBuffer.wrap(val);
        HistogramRecord r = null;
        while (vbb.hasRemaining()) {
            long start = sstart * HistogramSegment.GROUPING_FACTOR + vbb.getInt();

            long stop = sstart * HistogramSegment.GROUPING_FACTOR + vbb.getInt();
            int num = vbb.getShort();
            if ((interval.hasStart()) && (stop < interval.getStart())) {
                continue;
            }
            if ((interval.hasEnd()) && (start > interval.getEnd())) {
                if (r != null) {
                    records.add(r);
                }
                return true;
            }
            if (r == null) {
                r = new HistogramRecord(columnv, start, stop, num);
            } else {
                if (start - r.getStop() < mergeTime) {
                    r = new HistogramRecord(r.getColumnv(), r.getStart(), stop, r.getNumTuples() + num);
                } else {
                    records.add(r);
                    r = new HistogramRecord(columnv, start, stop, num);
                }
            }
        }
        if (r != null) {
            records.add(r);
        }
        return false;
    }

    @Override
    public boolean hasNext() {
        return !records.isEmpty();
    }

    @Override
    public HistogramRecord next() {
        if (records.isEmpty()) {
            throw new NoSuchElementException();
        }
        HistogramRecord r = records.poll();
        if (records.isEmpty() && !stopReached) {
            try {
                readNextSegments();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (RocksDBException e) {
                throw new UncheckedIOException(new IOException(e));
            }
        }
        return r;
    }
}
