package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class LV {
    private short length;
    private byte[] value;

    public LV(short length, byte[] value) {
        this.length = length;
        this.value = value;
    }

    public short getLength() {
        return length;
    }

    public byte[] getValue() {
        return value;
    }

    public static LV readLV(ByteBuffer buffer) {
        short length = Utils.getUnsignedByte(buffer);
        byte[] value = new byte[length];
        buffer.get(value);
        return new LV(length, value);
    }
}
