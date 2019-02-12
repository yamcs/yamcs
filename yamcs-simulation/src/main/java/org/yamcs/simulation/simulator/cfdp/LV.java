package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class LV {
    private byte[] value;

    public LV(byte[] value) {
        this.value = value;
    }

    public byte[] getValue() {
        return value;
    }

    public static LV readLV(ByteBuffer buffer) {
        byte[] value = new byte[Utils.getUnsignedByte(buffer)];
        buffer.get(value);
        return new LV(value);
    }

    public void writeToBuffer(ByteBuffer buffer) {
        Utils.writeUnsignedByte(buffer, (short) this.getValue().length);
        buffer.put(this.getValue());
    }
}
