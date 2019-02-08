package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum ConditionCode {

    NoError(0x00),
    AckLimitReached(0x01),
    KeepAliveReached(0x02),
    InvalidTransmissionMode(0x03),
    FilestoreRejection(0x04),
    FileChecksumFailure(0x05),
    FileSizeError(0x06),
    NakLimitReached(0x07),
    InactivityDetected(0x08),
    InvalidFileStructure(0x09),
    CheckLimitReached(0x0A),
    SuspendRequestReceived(0x0E),
    CancelRequestReceived(0x0F),
    Reserved(0x0B);

    private int code;

    public static final Map<Integer, ConditionCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(ConditionCode.values()),
            ConditionCode::getCode);

    private ConditionCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    private static ConditionCode fromCode(int code) {
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
        return ConditionCode.fromCode(b >> 4);
    }

}
