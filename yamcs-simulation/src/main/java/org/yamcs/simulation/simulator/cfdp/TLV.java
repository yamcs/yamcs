package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class TLV {
    private short type;
    private short length;
    private byte[] value;

    public TLV(short type, short length, byte[] value) {
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
}
