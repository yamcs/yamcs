package org.yamcs.parameterarchive;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.yarch.rocksdb.DescendingRangeIterator;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.DbIterator;

/**
 * Iterates over the segments of one partition for a parameter_id,
 * ParameterGroup_id, between a start and stop
 * 
 * @author nm
 *
 */
public class PartitionIterator {
    private SegmentKey currentKey;
    private final int parameterId, parameterGroupId;
    private final long start, stop;
    private final boolean ascending;
    SegmentEncoderDecoder segmentEncoder = new SegmentEncoderDecoder();
    private byte[] currentEngValueSegment;
    private byte[] currentRawValueSegment;
    private byte[] currentStatusSegment;
    final boolean retrieveEngValue;
    final boolean retrieveRawValue;
    final boolean retrieveParameterStatus;
    DbIterator dbIterator;
    boolean valid;

    public PartitionIterator(RocksIterator iterator, int parameterId, int parameterGroupId, long start, long stop,
            boolean ascending, boolean retrieveEngValue, boolean retrieveRawValue, boolean retrieveParameterStatus) {
        this.parameterId = parameterId;
        this.parameterGroupId = parameterGroupId;
        this.start = start;
        this.stop = stop;
        this.ascending = ascending;
        this.retrieveEngValue = retrieveEngValue;
        this.retrieveRawValue = retrieveRawValue;
        this.retrieveParameterStatus = retrieveParameterStatus;

        byte[] rangeStart = new SegmentKey(parameterId, parameterGroupId, SortedTimeSegment.getSegmentStart(start),
                (byte) 0).encode();
        byte[] rangeStop = new SegmentKey(parameterId, parameterGroupId, SortedTimeSegment.getSegmentStart(stop),
                Byte.MAX_VALUE).encode();
        if (ascending) {
            dbIterator = new AscendingRangeIterator(iterator, rangeStart, false, rangeStop, false);
        } else {
            dbIterator = new DescendingRangeIterator(iterator, rangeStart, false, rangeStop, false);
        }
        next();
    }

    public void next() {
        if (!dbIterator.isValid()) {
            valid = false;
            return;
        }
        if (ascending) {
            nextAscending();
        } else {
            nextDescending();
        }
    }

    void nextAscending() {
        currentKey = SegmentKey.decode(dbIterator.key());
        valid = true;

        SegmentKey key = currentKey;
        while (key.segmentStart == currentKey.segmentStart) {
            loadSegment(key.type);
            dbIterator.next();
            if (dbIterator.isValid()) {
                key = SegmentKey.decode(dbIterator.key());
            } else {
                break;
            }
        }
    }

    void nextDescending() {
        currentKey = SegmentKey.decode(dbIterator.key());
        valid = true;
        SegmentKey key = currentKey;

        while (key.segmentStart == currentKey.segmentStart) {
            loadSegment(key.type);
            dbIterator.prev();
            if (dbIterator.isValid()) {
                key = SegmentKey.decode(dbIterator.key());
            } else {
                break;
            }
        }
    }

    private void loadSegment(byte type) {
        if ((type == SegmentKey.TYPE_ENG_VALUE) && retrieveEngValue) {
            currentEngValueSegment = dbIterator.value();
        }
        if ((type == SegmentKey.TYPE_RAW_VALUE) && retrieveRawValue) {
            currentRawValueSegment = dbIterator.value();
        }
        if ((type == SegmentKey.TYPE_PARAMETER_STATUS) && retrieveParameterStatus) {
            currentStatusSegment = dbIterator.value();
        }
    }

    SegmentKey key() {
        return currentKey;
    }

    ValueSegment engValue() {
        if (currentEngValueSegment == null) {
            return null;
        }
        try {
            return (ValueSegment) segmentEncoder.decode(currentEngValueSegment, currentKey.segmentStart);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    ValueSegment rawValue() {
        if (currentRawValueSegment == null) {
            return null;
        }
        try {
            return (ValueSegment) segmentEncoder.decode(currentRawValueSegment, currentKey.segmentStart);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    ParameterStatusSegment parameterStatus() {
        if (currentStatusSegment == null) {
            return null;
        }
        try {
            return (ParameterStatusSegment) segmentEncoder.decode(currentStatusSegment, currentKey.segmentStart);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    boolean isValid() {
        return valid;
    }

    public int getParameterGroupId() {
        return parameterGroupId;
    }

    public int getParameterId() {
        return parameterId;
    }
}
