package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.utils.CfdpUtils;

public class TLV {
    private byte type;
    private byte[] value;

    public TLV(byte type, byte[] value) {
        this.type = type;
        this.value = value;
    }

    public byte getType() {
        return type;
    }

    public byte[] getValue() {
        return value;
    }

    public static TLV readTLV(ByteBuffer buffer) {
        byte type = buffer.get();
        byte[] value = new byte[CfdpUtils.getUnsignedByte(buffer)]; // get length from buffer
        buffer.get(value);
        return new TLV(type, value);
    }

    public void writeToBuffer(ByteBuffer buffer) {
        buffer.put(this.getType());
        CfdpUtils.writeUnsignedByte(buffer, (short) this.getValue().length);
        buffer.put(this.getValue());
    }
}
