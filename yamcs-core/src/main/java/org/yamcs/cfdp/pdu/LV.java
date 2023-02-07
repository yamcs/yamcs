package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.utils.ByteArrayUtils;

public class LV {
    private byte[] value;

    public LV(byte[] value) {
        this.value = value;
        // TODO: check length > 255
    }

    public LV(String value) {
        this(value.getBytes());
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

    public byte[] getBytes() {
        return Bytes.concat(new byte[] {(byte) value.length}, value);
    }

    @Override
    public String toString() {
        return new String(value);
    }
}
