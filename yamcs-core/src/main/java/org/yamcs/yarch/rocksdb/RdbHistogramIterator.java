package org.yamcs.yarch.rocksdb;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.ZERO_BYTES;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.rocksdb.RocksDBException;
import org.yamcs.logging.Log;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.HistogramEncoder;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TableDefinition;

public class RdbHistogramIterator implements HistogramIterator {

    private Iterator<PartitionManager.Interval> partitionIterator;
    private AscendingRangeIterator segmentIterator;

    private Deque<HistogramRecord> records = new ArrayDeque<>();

    private final TimeInterval interval;
    private final Tablespace tablespace;

    YRDB rdb;

    Log log;
    String colName;
    boolean stopReached = false;
    RdbPartitionManager partMgr;
    HistogramEncoder encoder;

    public RdbHistogramIterator(String yamcsInstance, Tablespace tablespace, TableDefinition tblDef,
            String colName, TimeInterval interval) throws RocksDBException, IOException {
        this.encoder = HistogramEncoder.createEncoder(tblDef);
        this.interval = interval;
        this.colName = colName;
        this.tablespace = tablespace;

        partMgr = tablespace.getTable(tblDef).getPartitionManager();
        partitionIterator = partMgr.intervalIterator(interval);
        log = new Log(getClass(), yamcsInstance);
        log.setContext(partMgr.getTableName());
        readNextPartition();
    }

    private void readNextPartition() throws IOException {
        if (!partitionIterator.hasNext()) {
            stopReached = true;
            return;
        }

        if (rdb != null) {
            tablespace.dispose(rdb);
            rdb = null;
        }

        PartitionManager.Interval intv = partitionIterator.next();

        RdbHistogramInfo hist = (RdbHistogramInfo) intv.getHistogram(colName);
        if (hist == null) {
            readNextPartition();
            return;
        }
        rdb = tablespace.getRdb(hist.partitionDir, false);

        long segStart = interval.hasStart() ? encoder.segIndex(encoder.segmentStart(interval.getStart())) : 0;
        byte[] dbKeyStart = encoder.encodeKey(hist.tbsIndex, segStart, ZERO_BYTES);

        byte[] dbKeyStop;
        if (interval.hasEnd()) {
            dbKeyStop = ByteArrayUtils.encodeInt(hist.tbsIndex, new byte[12], 0);
            long segStop = encoder.segIndex(encoder.segmentStart(interval.getEnd()));
            ByteArrayUtils.encodeLong(segStop, dbKeyStop, TBS_INDEX_SIZE);
        } else {
            dbKeyStop = RdbStorageEngine.dbKey(hist.tbsIndex);
        }
        if (segmentIterator != null) {
            segmentIterator.close();
        }

        try {
            segmentIterator = new AscendingRangeIterator(rdb.newIterator(), dbKeyStart, dbKeyStop);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        readNextSegments();
    }

    // reads all the segments with the same index, or continues until records are found
    private void readNextSegments() throws IOException {
        if (!segmentIterator.isValid()) {
            readNextPartition();
            return;
        }

        HistogramSegment segment = encoder.decodeSegment(segmentIterator.key(), segmentIterator.value());
        long segIndex = segment.segIndex();

        while (true) {
            boolean beyondStop = addRecords(segment);
            if (beyondStop) {
                stopReached = true;
            }

            segmentIterator.next();
            if (!segmentIterator.isValid()) {
                readNextPartition();
                break;
            }
            segment = encoder.decodeSegment(segmentIterator.key(), segmentIterator.value());
            if (segment.segIndex() != segIndex && !records.isEmpty()) {
                break;
            }
        }
    }

    @Override
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
    private boolean addRecords(HistogramSegment segment) {
        HistogramRecord r = null;

        for (HistogramSegment.SegRecord record : segment.getRecords()) {
            long start = encoder.joinTime(segment.segIndex(), record.dstart());
            long stop = encoder.joinTime(segment.segIndex(), record.dstop());

            if ((interval.hasStart()) && (stop <= interval.getStart())) {
                continue;
            }
            if ((interval.hasEnd()) && (start > interval.getEnd())) {
                if (r != null) {
                    records.addLast(r);
                }
                return true;
            }
            if (r == null) {
                r = new HistogramRecord(segment.columnv(), start, stop, record.num());
            } else {
                records.addLast(r);
                r = new HistogramRecord(segment.columnv(), start, stop, record.num());
            }
        }
        if (r != null) {
            records.addLast(r);
        }
        return false;
    }

    @Override
    public void seek(byte[] columnValue, long time) {
        try {
            records.clear();
            long segmentStartTime = encoder.segmentStart(time);
            interval.setStart(segmentStartTime);
            partitionIterator = partMgr.intervalIterator(interval);
            if (!partitionIterator.hasNext()) {
                stopReached = true;
                return;
            }

            PartitionManager.Interval intv = partitionIterator.next();
            RdbHistogramInfo hist = (RdbHistogramInfo) intv.getHistogram(colName);

            if (hist == null) {
                readNextPartition();
                return;
            }

            rdb = tablespace.getRdb(hist.partitionDir, false);
            long startIdx = encoder.segIndex(time);
            byte[] dbKeyStart = encoder.encodeKey(hist.tbsIndex, startIdx, columnValue);

            byte[] dbKeyStop;
            if (interval.hasEnd()) {
                long stopIdx = encoder.segIndex(interval.getEnd());
                dbKeyStop = encoder.encodeKey(hist.tbsIndex, stopIdx, new byte[] {});
            } else {
                dbKeyStop = RdbStorageEngine.dbKey(hist.tbsIndex);
            }
            if (segmentIterator != null) {
                segmentIterator.close();
            }
            segmentIterator = new AscendingRangeIterator(rdb.newIterator(), dbKeyStart, dbKeyStop);
            if (!segmentIterator.isValid()) {
                readNextPartition();
                return;
            }

            // we have to add the first record only partially and only if it starts at the time and value of the seek
            var segment = encoder.decodeSegment(segmentIterator.key(), segmentIterator.value());

            if (segment.segIndex() != startIdx || !Arrays.equals(columnValue, segment.columnv())) {
                readNextSegments();
                return;
            }

            addRecords(segment);

            HistogramRecord r;
            while ((r = records.poll()) != null) {
                if (!Arrays.equals(columnValue, r.getColumnv()) || r.getStart() > time) {
                    records.addFirst(r);
                    break;
                }
            }
            segmentIterator.next();
            readNextSegments();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RocksDBException e) {
            throw new UncheckedIOException(new IOException(e));
        }
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
            }
        }
        return r;
    }

}
