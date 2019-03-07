package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.CfdpUtils;

public class NakPacket extends CfdpPacket {

    private long scopeStart;
    private long scopeEnd;
    private List<SegmentRequest> segmentRequests = new ArrayList<SegmentRequest>();

    public NakPacket(long scopeStart, long scopeEnd, List<SegmentRequest> requests, CfdpHeader header) {
        super(header);
        this.scopeStart = scopeStart;
        this.scopeEnd = scopeEnd;
        this.segmentRequests = requests;
        finishConstruction();
    }

    public NakPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        this.scopeStart = CfdpUtils.getUnsignedInt(buffer);
        this.scopeEnd = CfdpUtils.getUnsignedInt(buffer);
        while (buffer.hasRemaining()) {
            segmentRequests.add(new SegmentRequest(CfdpUtils.getUnsignedInt(buffer),
                    CfdpUtils.getUnsignedInt(buffer)));
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        CfdpUtils.writeUnsignedInt(buffer, scopeStart);
        CfdpUtils.writeUnsignedInt(buffer, scopeEnd);
        segmentRequests.forEach(x -> x.writeToBuffer(buffer));
    }

    @Override
    protected int calculateDataFieldLength() {
        return 9 + 8 * segmentRequests.size();
    }
}
