package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.CfdpUtils;

public class NakPacket extends CfdpPacket {

    private long scopeStart;
    private long scopeEnd;
    private List<SegmentRequest> segmentRequests = new ArrayList<SegmentRequest>();

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
    protected CfdpHeader createHeader() {
        // TODO Auto-generated method stub
        return null;
    }
}
