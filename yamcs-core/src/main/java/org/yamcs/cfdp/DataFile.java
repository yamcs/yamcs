package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.yamcs.cfdp.pdu.SegmentRequest;

public class DataFile {

    private SortedMap<Long, DataFileSegment> dataFileSegments = new TreeMap<Long, DataFileSegment>();
    private long maxSize; // -1 == unbounded

    public DataFile() {
        this(-1);
    }

    public DataFile(long maxSize) {
        this.maxSize = maxSize;
    }

    public void addSegment(DataFileSegment segment) {
        if (dataFileSegments.containsKey(segment.getOffset())) {
            // we already have received a segment for this offset, ignoring this one.
        } else {
            dataFileSegments.put(segment.getOffset(), segment);
        }
    }

    // returns a list of offset pairs of missing data chunks (that can represent one or more DataFileSegments)
    public List<SegmentRequest> getMissingChunks() {
        List<SegmentRequest> toReturn = new ArrayList<SegmentRequest>();
        long startOffset = 0;
        long endOffset = 0;
        for (DataFileSegment segment : this.dataFileSegments.values()) {
            if (segment.getOffset() != startOffset) {
                endOffset = segment.getOffset();
                toReturn.add(new SegmentRequest(startOffset, endOffset));
            }
            startOffset = segment.getOffset() + segment.getLength();
        }
        if (maxSize != -1 && startOffset != maxSize) {
            toReturn.add(new SegmentRequest(startOffset, maxSize));
        }
        return toReturn;
    }

}
