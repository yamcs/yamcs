package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class TLV {
    private byte type;
    private short length;
    private byte[] value;

    public TLV(byte type, short length, byte[] value) {
        this.type = type;
        this.length = length;
        this.value = value;
    }

    public short getType() {
        return type;
    }

    public short getLength() {
        return length;
    }

    public byte[] getValue() {
        return value;
    }

    public static TLV readTLV(ByteBuffer buffer) {
        byte type = buffer.get();
        short length = Utils.getUnsignedByte(buffer);
        byte[] value = new byte[length];
        buffer.get(value);
        return new TLV(type, length, value);
    }

    public void writeToBuffer(ByteBuffer buffer) {
        buffer.put(this.getValue());
        Utils.writeUnsignedByte(buffer, this.length);
        buffer.put(this.value);
    }
}
