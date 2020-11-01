package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum StatusCode {

    SUCCESSFUL((byte) 0x00),
    CREATE_NOT_ALLOWED((byte) 0x01),
    NOT_PERFORMED((byte) 0x0f),
    FILE_DOES_NOT_EXIST((byte) 0x01),
    DELETE_NOT_ALLOWED((byte) 0x02),
    OLD_FILE_NAME_DOES_NOT_EXIST((byte) 0x01),
    NEW_FILE_NAME_ALREADY_EXISTS((byte) 0x02),
    RENAME_NOT_ALLOWED((byte) 0x03),
    FILE_NAME_DOES_NOT_EXIST((byte) 0x01),
    FILE_NAME_2_DOES_NOT_EXIST((byte) 0x02),
    APPEND_NOT_ALLOWED((byte) 0x03),
    REPLACE_NOT_ALLOWED((byte) 0x03),
    DIRECTORY_CANNOT_BE_CREATED((byte) 0x01),
    DIRECTORY_DOES_NOT_EXIST((byte) 0x01);

    private byte code;

    public static final Map<Byte, StatusCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(StatusCode.values()),
            StatusCode::getCode);

    private StatusCode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return this.code;
    }

    private static StatusCode fromCode(byte code) {
        if (Lookup.containsKey(code)) {
            return Lookup.get(code);
        }
        throw new IllegalArgumentException("Invalid CFDP Status code: " + code);
    }

    public static StatusCode readStatusCode(ByteBuffer buffer) {
        return readStatusCode(buffer.get());
    }

    public static StatusCode readStatusCode(byte b) {
        return StatusCode.fromCode((byte) (b & 0x0f));
    }
}
