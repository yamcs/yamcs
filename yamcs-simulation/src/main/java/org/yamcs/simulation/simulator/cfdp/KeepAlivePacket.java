package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

import org.yamcs.utils.CfdpUtils;

public class KeepAlivePacket extends CfdpPacket {

    private long progress;

    public KeepAlivePacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        this.progress = CfdpUtils.getUnsignedInt(buffer);
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        CfdpUtils.writeUnsignedInt(buffer, progress);
    }

    @Override
    protected CfdpHeader createHeader() {
        // TODO Auto-generated method stub
        return null;
    }

}
