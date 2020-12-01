package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum ConditionCode {

    NO_ERROR((byte) 0x00),
    ACK_LIMIT_REACHED((byte) 0x01),
    KEEP_ALIVE_LIMIT_REACHED((byte) 0x02),
    INVALID_TRANSMISSION_MODE((byte) 0x03),
    FILESTORE_REJECTION((byte) 0x04),
    FILE_CHECKSUM_FAILURE((byte) 0x05),
    FILE_SIZE_ERROR((byte) 0x06),
    NAK_LIMIT_REACHED((byte) 0x07),
    INACTIVITY_DETECTED((byte) 0x08),
    INVALID_FILE_STRUCTURE((byte) 0x09),
    CHECK_LIMIT_REACHED((byte) 0x0A),
    UNSUPPORTED_CHECKSUM_TYPE((byte)0x0B),
    SUSPEND_REQUEST_RECEIVED((byte) 0x0E),
    CANCEL_REQUEST_RECEIVED((byte) 0x0F),
    RESERVED((byte) 0x0C);

    private byte code;

    public static final Map<Byte, ConditionCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(ConditionCode.values()),
            ConditionCode::getCode);

    private ConditionCode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    private static ConditionCode fromCode(byte code) {
        if (Lookup.containsKey(code)) {
            return Lookup.get(code);
        } else {
            return RESERVED;
        }
    }

    public static ConditionCode readConditionCode(byte b) {
        return ConditionCode.fromCode((byte) ((b >> 4)&0x0F));
    }

    public void writeAsByteToBuffer(ByteBuffer buffer) {
        buffer.put((byte) (getCode() << 4));
    }

}
