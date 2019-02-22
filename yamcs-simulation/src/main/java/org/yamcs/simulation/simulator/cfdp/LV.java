package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

import org.yamcs.utils.CfdpUtils;

public class LV {
    private byte[] value;

    public LV(byte[] value) {
        this.value = value;
    }

    public byte[] getValue() {
        return value;
    }

    public static LV readLV(ByteBuffer buffer) {
        byte[] value = new byte[CfdpUtils.getUnsignedByte(buffer)];
        buffer.get(value);
        return new LV(value);
    }

    public void writeToBuffer(ByteBuffer buffer) {
        CfdpUtils.writeUnsignedByte(buffer, (short) this.getValue().length);
        buffer.put(this.getValue());
    }
}
