package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum ConditionCode {

    NoError((byte) 0x00),
    AckLimitReached((byte) 0x01),
    KeepAliveReached((byte) 0x02),
    InvalidTransmissionMode((byte) 0x03),
    FilestoreRejection((byte) 0x04),
    FileChecksumFailure((byte) 0x05),
    FileSizeError((byte) 0x06),
    NakLimitReached((byte) 0x07),
    InactivityDetected((byte) 0x08),
    InvalidFileStructure((byte) 0x09),
    CheckLimitReached((byte) 0x0A),
    SuspendRequestReceived((byte) 0x0E),
    CancelRequestReceived((byte) 0x0F),
    Reserved((byte) 0x0B);

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
            return Reserved;
        }
    }

    public static ConditionCode readConditionCode(ByteBuffer buffer) {
        return ConditionCode.fromCode(buffer.get());
    }

    public static ConditionCode readConditionCode(byte b) {
        return ConditionCode.fromCode((byte) (b >> 4));
    }

    public void writeAsByteToBuffer(ByteBuffer buffer) {
        buffer.put((byte) (getCode() << 4));
    }

}
