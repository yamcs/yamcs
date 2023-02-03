package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.utils.StringConverter;
/**
 * Type, Length, Value 
 *
 */
public class TLV {
    static final byte TYPE_FILE_STORE_REQUEST= 0x00;
    static final byte TYPE_FILE_STORE_RESPONSE = 0x01;
    public static final byte TYPE_MESSAGE_TO_USER = 0x02;
    static final byte TYPE_FAULT_HANDLER_OVERRIDE = 0x04;
    static final byte TYPE_FLOW_LABEL = 0x05;
    static final byte TYPE_ENTITY_ID= 0x06;
    
    
    private byte type;
    private byte[] value;

    public TLV(byte type, byte[] value) {
        this.type = type;
        this.value = value;
        // TODO: check length > 255
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
        buffer.put(type);
        CfdpUtils.writeUnsignedByte(buffer, value.length);
        buffer.put(value);
    }

    public byte[] getBytes() {
        return Bytes.concat(new byte[] {type, (byte) value.length }, value);
    }

    public static TLV getEntityIdTLV(long entityId, int entityIdLength) {
        return new TLV(TYPE_ENTITY_ID, CfdpUtils.longToBytesFixed(entityId, entityIdLength));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + type;
        result = prime * result + Arrays.hashCode(value);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TLV other = (TLV) obj;
        if (type != other.type)
            return false;
        if (!Arrays.equals(value, other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "TLV [type=" + type + ", value=" + StringConverter.arrayToHexString(value) + "]";
    }

    public String toJson() {
        return "{type=" + type + ", length=" + value.length + ", value=" + StringConverter.arrayToHexString(value) + "}";
    }
}
