package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NakPacket extends Packet {

    private long scopeStart;
    private long scopeEnd;
    private List<SegmentRequest> segmentRequests = new ArrayList<SegmentRequest>();

    public NakPacket(ByteBuffer buffer, Header header) {
        super(buffer, header);

        this.scopeStart = Utils.getUnsignedInt(buffer);
        this.scopeEnd = Utils.getUnsignedInt(buffer);
        while (buffer.hasRemaining()) {
            segmentRequests.add(new SegmentRequest(Utils.getUnsignedInt(buffer),
                    Utils.getUnsignedInt(buffer)));
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        super.writeCFDPPacket(buffer);
        Utils.writeUnsignedInt(buffer, scopeStart);
        Utils.writeUnsignedInt(buffer, scopeEnd);
        segmentRequests.forEach(x -> x.writeToBuffer(buffer));
    }
}
