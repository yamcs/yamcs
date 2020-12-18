package org.yamcs.simulator.cfdp;

import java.nio.ByteBuffer;

import org.yamcs.simulator.SimulatorCcsdsPacket;

public class CfdpCcsdsPacket extends SimulatorCcsdsPacket {
    public static final int APID = 2045;
    
    public CfdpCcsdsPacket(int pduLength) {
        super(ByteBuffer.allocate(6+pduLength));
        setHeader(APID, 1, 0, 3, getSeq(APID));
    }
    
    public CfdpCcsdsPacket(byte[] packet) {
        super(packet);
    }

    @Override
    public ByteBuffer getUserDataBuffer() {
        bb.position(6);
        return bb.slice();
    }

    @Override
    protected void fillChecksum() {
    }
}
