package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

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

    public void writeToBuffer(ByteBuffer buffer) {
        Utils.writeUnsignedInt(buffer, segmentStart);
        Utils.writeUnsignedInt(buffer, segmentEnd);
    }
}
