package org.yamcs.simulator;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.tctm.CcsdsPacket;

public abstract class SimulatorCcsdsPacket extends CcsdsPacket {
    protected static HashMap<Integer, AtomicInteger> seqMap = new HashMap<>(2); // apid -> seq

    
    public SimulatorCcsdsPacket(byte[] packet) {
        super(packet);
    }

    public SimulatorCcsdsPacket(ByteBuffer bb) {
        super(bb);
    }

    public abstract ByteBuffer getUserDataBuffer();

    protected abstract void fillChecksum();

    protected static int getSeq(int apid) {
        AtomicInteger seq = seqMap.computeIfAbsent(apid, a -> new AtomicInteger(0));
        return seq.getAndIncrement() & 0xFFFF;
    }
}
