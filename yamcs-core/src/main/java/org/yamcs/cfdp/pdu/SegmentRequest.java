package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Objects;

import org.yamcs.cfdp.CfdpUtils;

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
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof SegmentRequest)) {
            return false;
        }
        if (o == this) {
            return true;
        }
        return this.getSegmentStart() == ((SegmentRequest) o).getSegmentStart()
                && this.getSegmentEnd() == ((SegmentRequest) o).getSegmentEnd();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSegmentStart(), getSegmentEnd());
    }
    
    @Override
    public String toString() {
        return "SegmentRequest [segmentStart=" + segmentStart + ", segmentEnd=" + segmentEnd + "]";
    }

}
