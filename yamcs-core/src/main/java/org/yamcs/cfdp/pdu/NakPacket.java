package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.cfdp.FileDirective;
import org.yamcs.cfdp.CfdpUtils;

public class NakPacket extends CfdpPacket implements FileDirective {

    private long scopeStart;
    private long scopeEnd;
    private List<SegmentRequest> segmentRequests = new ArrayList<SegmentRequest>();

    public NakPacket(long scopeStart, long scopeEnd, List<SegmentRequest> requests, CfdpHeader header) {
        super(header);
        this.scopeStart = scopeStart;
        this.scopeEnd = scopeEnd;
        this.segmentRequests = requests;
    }

    NakPacket(ByteBuffer buffer, CfdpHeader header) {
        super(header);

        this.scopeStart = CfdpUtils.getUnsignedInt(buffer);
        this.scopeEnd = CfdpUtils.getUnsignedInt(buffer);
        while (buffer.hasRemaining()) {
            segmentRequests.add(new SegmentRequest(CfdpUtils.getUnsignedInt(buffer),
                    CfdpUtils.getUnsignedInt(buffer)));
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(getFileDirectiveCode().getCode());
        CfdpUtils.writeUnsignedInt(buffer, scopeStart);
        CfdpUtils.writeUnsignedInt(buffer, scopeEnd);
        segmentRequests.forEach(x -> x.writeToBuffer(buffer));
    }

    @Override
    public int getDataFieldLength() {
        return 9 + 8 * segmentRequests.size();
    }

    /**
     * returns the maximum number of segments which can be transmitted given the maximum data size of a PDU
     */
    public static int maxNumSegments(int maxDataSize) {
        return (maxDataSize - 9) / 8;
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.NAK;
    }

    public List<SegmentRequest> getSegmentRequests() {
        return this.segmentRequests;
    }

    public long getScopeStart() {
        return scopeStart;
    }

    public long getScopeEnd() {
        return scopeEnd;
    }

    @Override
    public String toString() {
        return "NakPacket [scopeStart=" + scopeStart + ", scopeEnd=" + scopeEnd + ", segmentRequests=" + segmentRequests
                + "]";
    }

}
