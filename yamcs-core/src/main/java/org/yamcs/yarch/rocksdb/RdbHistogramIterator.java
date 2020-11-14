package org.yamcs.yarch.rocksdb;

import static org.yamcs.yarch.HistogramSegment.segmentStart;
import static org.yamcs.yarch.rocksdb.RdbHistogramInfo.histoDbKey;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
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
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TableDefinition;

/**
 * 
 * @author nm
 *
 */
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
    
    public RdbHistogramIterator(String yamcsInstance, Tablespace tablespace, TableDefinition tblDef,
            String colName, TimeInterval interval) throws RocksDBException, IOException {
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

        long segStart = interval.hasStart() ? segmentStart(interval.getStart()) : 0;
        byte[] dbKeyStart = histoDbKey(hist.tbsIndex, segStart, ZERO_BYTES);

        boolean strictEnd;
        byte[] dbKeyStop;
        if (interval.hasEnd()) {
            strictEnd = false;
            dbKeyStop = ByteArrayUtils.encodeInt(hist.tbsIndex, new byte[12], 0);
            long segStop = segmentStart(interval.getEnd());
            ByteArrayUtils.encodeLong(segStop, dbKeyStop, TBS_INDEX_SIZE);
        } else {
            dbKeyStop = RdbStorageEngine.dbKey(hist.tbsIndex);
            strictEnd = false;
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

    // reads all the segments with the same sstart time
    private void readNextSegments() throws IOException {
        if (!segmentIterator.isValid()) {
            readNextPartition();
            return;
        }
        long sstart = ByteArrayUtils.decodeLong(segmentIterator.key(), RdbStorageEngine.TBS_INDEX_SIZE);

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
            long g = ByteArrayUtils.decodeLong(segmentIterator.key(), RdbStorageEngine.TBS_INDEX_SIZE);
            if (g != sstart && !records.isEmpty()) {
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
    private boolean addRecords(byte[] key, byte[] val) {
        long sstart = ByteArrayUtils.decodeLong(key, RdbStorageEngine.TBS_INDEX_SIZE);
        byte[] columnv = new byte[key.length - RdbStorageEngine.TBS_INDEX_SIZE - 8];
        System.arraycopy(key, RdbStorageEngine.TBS_INDEX_SIZE + 8, columnv, 0, columnv.length);

        ByteBuffer vbb = ByteBuffer.wrap(val);
        HistogramRecord r = null;
        while (vbb.hasRemaining()) {
            long start = sstart * HistogramSegment.GROUPING_FACTOR + vbb.getInt();

            long stop = sstart * HistogramSegment.GROUPING_FACTOR + vbb.getInt();
            int num = vbb.getShort();
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
                r = new HistogramRecord(columnv, start, stop, num);
            } else {
                records.addLast(r);
                r = new HistogramRecord(columnv, start, stop, num);
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
            long sstart = segmentStart(time);
            interval.setStart(HistogramSegment.GROUPING_FACTOR * sstart);
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
            long segStart = segmentStart(time);
            byte[] dbKeyStart = histoDbKey(hist.tbsIndex, segStart, columnValue);

            byte[] dbKeyStop;
            if (interval.hasEnd()) {
                dbKeyStop = ByteArrayUtils.encodeInt(hist.tbsIndex, new byte[12], 0);
                long segStop = segmentStart(interval.getEnd());
                ByteArrayUtils.encodeLong(segStop, dbKeyStop, TBS_INDEX_SIZE);
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
            byte[] key = segmentIterator.key();
            long sstart1 = ByteArrayUtils.decodeLong(key, TBS_INDEX_SIZE);
            byte[] columnValue1 = new byte[key.length - TBS_INDEX_SIZE - 8];
            System.arraycopy(key, TBS_INDEX_SIZE + 8, columnValue1, 0, columnValue1.length);
            
            if (sstart1 != sstart || !Arrays.equals(columnValue, columnValue1)) {
                readNextSegments();
                return;
            }

            addRecords(segmentIterator.key(), segmentIterator.value());

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
