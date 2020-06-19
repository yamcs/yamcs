package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.utils.StringConverter;
/**
 * Type, Length, Value 
 *
 */
public class TLV {
   


    static final byte TYPE_FileStoreRequest= 0x00;
    static final byte TYPE_FileStoreResponse = 0x01;
    static final byte TYPE_MessageToUser = 0x02;
    static final byte TYPE_FaultHandlerOverride = 0x04;
    static final byte TYPE_FlowLabel = 0x05;
    static final byte TYPE_EntityId= 0x06;
    
    
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
        buffer.put(type);
        CfdpUtils.writeUnsignedByte(buffer, value.length);
        buffer.put(value);
    }
    

    public static TLV getEntityIdTLV(long entityId, int entityIdLength) {
        return new TLV(TYPE_EntityId, CfdpUtils.longToBytes(entityId, entityIdLength));
    }
    
    @Override
    public String toString() {
        return "TLV [type=" + type + ", value=" + StringConverter.arrayToHexString(value) + "]";
    }
    
}
