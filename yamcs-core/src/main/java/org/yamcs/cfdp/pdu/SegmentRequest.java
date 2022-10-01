package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.CfdpUtils;

/**
 * The SegmentRequest is part of a NAK PDU to indicate a missing part of a file.
 * <p>
 * segmentStart=segmentEnd=0 means that the metadata PDU was missing.
 *
 */
public class SegmentRequest {
    private long segmentStart;
    private long segmentEnd;

    public SegmentRequest(long start, long end) {
        this.segmentStart = start;
        this.segmentEnd = end;
    }

    public long getSegmentStart() {
        return this.segmentStart;
    }

    public long getSegmentEnd() {
        return this.segmentEnd;
    }

    public boolean isMetadata() {
        return segmentStart == 0 && segmentEnd == 0;
    }

    // returns true if a given value false within the range of this SegmentRequest, including the start but excluding
    // the end
    public boolean isInRange(long value) {
        return value >= this.segmentStart && value < this.segmentEnd;
    }

    public void writeToBuffer(ByteBuffer buffer) {
        CfdpUtils.writeUnsignedInt(buffer, segmentStart);
        CfdpUtils.writeUnsignedInt(buffer, segmentEnd);
    }

    @Override
    public String toString() {
        return segmentStart + "-" + segmentEnd;
    }
}
