package org.yamcs.parameterarchive;

import org.rocksdb.RocksIterator;
import org.yamcs.utils.DecodingException;

/**
 * Iterates over the segments of one partition for a parameter_id, ParameterGroup_id, between a start and stop
 * 
 * @author nm
 *
 */
public class PartitionIterator {
    private final RocksIterator iterator;
    private boolean valid=false;
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


    public PartitionIterator(RocksIterator iterator, int parameterId, int parameterGroupId, long start, long stop, boolean ascending, boolean retrieveEngValue, boolean retrieveRawValue, boolean retrieveParameterStatus) {
        this.parameterId = parameterId;
        this.parameterGroupId = parameterGroupId;
        this.start = start;
        this.stop = stop;
        this.ascending = ascending;
        this.retrieveEngValue = retrieveEngValue;
        this.retrieveRawValue = retrieveRawValue;
        this.retrieveParameterStatus = retrieveParameterStatus;
        
        this.iterator = iterator;
        if(ascending) {
            goToFirstAscending();
        } else {
            goToFirstDescending();
        }

    }

    private void goToFirstDescending() {
        iterator.seek(new SegmentKey(parameterId, parameterGroupId, SortedTimeSegment.getSegmentStart(stop), Byte.MAX_VALUE).encode());
        if(!iterator.isValid()) {
            iterator.seekToLast();
        } else {
            currentKey = SegmentKey.decode(iterator.key());
            if((currentKey.parameterGroupId != parameterGroupId) || (currentKey.parameterId!=parameterId) || currentKey.segmentStart>SortedTimeSegment.getSegmentStart(stop)) {
                iterator.prev();
            }
        }

        if(!iterator.isValid()) {
            valid = false;
        } else {
            nextDescending();
        }
    }

    private void goToFirstAscending() {
        iterator.seek(new SegmentKey(parameterId, parameterGroupId, SortedTimeSegment.getSegmentStart(start), (byte)0).encode());
        if(!iterator.isValid()) {
            valid = false;
        } else {
            nextAscending();
        }
    }

    public void next() {
        if(!iterator.isValid()) {
            valid = false;
            return;
        }
        if(ascending) {
            nextAscending();
        } else {
            nextDescending();
        }
    }

    void nextAscending() {
        currentKey = SegmentKey.decode(iterator.key());
        if((currentKey.parameterGroupId != parameterGroupId) || (currentKey.parameterId != parameterId) || (currentKey.segmentStart>stop)) {
            valid = false;
            return;
        } 
        valid = true;

        SegmentKey key = currentKey;
        while((key.segmentStart == currentKey.segmentStart) && (key.parameterGroupId==parameterGroupId) && (key.parameterId==parameterId)) {
            loadSegment(key.type);
            iterator.next();
            if(iterator.isValid()) {
                key = SegmentKey.decode(iterator.key());
            } else {
                break;
            }
        }
    }

    void nextDescending() {
        currentKey = SegmentKey.decode(iterator.key());
        if((currentKey.parameterGroupId!=parameterGroupId) || (currentKey.parameterId!=parameterId) || (currentKey.segmentStart < SortedTimeSegment.getSegmentStart(start))) {
            valid = false;
            return;
        }
        valid = true;
        SegmentKey key = currentKey;

        while((key.segmentStart == currentKey.segmentStart) && (key.parameterGroupId==parameterGroupId) && (key.parameterId==parameterId)) {
            loadSegment(key.type);
            iterator.prev();
            if(iterator.isValid()) {
                key = SegmentKey.decode(iterator.key());
            } else {
                break;
            }
        }
    }

    private void loadSegment(byte type) {
        if((type==SegmentKey.TYPE_ENG_VALUE) && retrieveEngValue) {
            currentEngValueSegment = iterator.value();
        }
        if((type==SegmentKey.TYPE_RAW_VALUE) && retrieveRawValue) {
            currentRawValueSegment = iterator.value();
        }
        if((type==SegmentKey.TYPE_PARAMETER_STATUS) && retrieveParameterStatus) {
            currentStatusSegment = iterator.value();
        }
    }

    SegmentKey key() {
        return currentKey;
    }

    BaseSegment engValue() throws DecodingException {
        if(currentEngValueSegment ==null) return null;
        return (BaseSegment) segmentEncoder.decode(currentEngValueSegment, currentKey.segmentStart);
    } 

    BaseSegment rawValue() throws DecodingException {
        if(currentRawValueSegment ==null) return null;
        return (BaseSegment) segmentEncoder.decode(currentRawValueSegment, currentKey.segmentStart);
    }

    ParameterStatusSegment parameterStatus() throws DecodingException {
        if(currentStatusSegment==null) return null;
        return (ParameterStatusSegment) segmentEncoder.decode(currentStatusSegment, currentKey.segmentStart);
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
