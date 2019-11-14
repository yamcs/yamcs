package org.yamcs.cfdp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    // returns the amount of bytes received of this Data Files.
    // Missing intermediate chunks are not yet received and are therefore not counted
    public long getReceivedSize() {
        return this.dataFileSegments.values().stream().map(DataFileSegment::getLength).mapToInt(Integer::intValue)
                .sum();
    }

    /**
     * public long getSize() { Long last = dataFileSegments.lastKey(); return last +
     * dataFileSegments.get(last).getLength(); }
     */

    public byte[] getData() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        dataFileSegments.entrySet().stream().forEach(e -> {
            try {
                baos.write((byte[]) e.getValue().getData());
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
        });
        return baos.toByteArray();
    }

    /**
     * 
     * @return true if all the data has been received
     */
    public boolean isComplete() {
        long startOffset = 0;
        for (DataFileSegment segment : this.dataFileSegments.values()) {
            if (segment.getOffset() != startOffset) {
               return false;
            }
            startOffset = segment.getOffset() + segment.getLength();
        }
        if (maxSize != -1 && startOffset != maxSize) {
            return false;
        }
        return true;
    }
}
