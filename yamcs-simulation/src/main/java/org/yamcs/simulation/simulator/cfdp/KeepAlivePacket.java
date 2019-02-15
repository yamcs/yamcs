package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class KeepAlivePacket extends CfdpPacket {

    private long progress;

    public KeepAlivePacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        this.progress = Utils.getUnsignedInt(buffer);
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        Utils.writeUnsignedInt(buffer, progress);
    }

    @Override
    protected CfdpHeader createHeader() {
        // TODO Auto-generated method stub
        return null;
    }

}
