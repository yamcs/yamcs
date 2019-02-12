package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class TLV {
    private byte type;
    private byte[] value;

    public TLV(byte type, byte[] value) {
        this.type = type;
        this.value = value;
    }

    public short getType() {
        return type;
    }

    public byte[] getValue() {
        return value;
    }

    public static TLV readTLV(ByteBuffer buffer) {
        byte type = buffer.get();
        byte[] value = new byte[Utils.getUnsignedByte(buffer)]; // get length from buffer
        buffer.get(value);
        return new TLV(type, value);
    }

    public void writeToBuffer(ByteBuffer buffer) {
        buffer.put(this.getValue());
        Utils.writeUnsignedByte(buffer, (short) this.getValue().length);
        buffer.put(this.value);
    }
}
