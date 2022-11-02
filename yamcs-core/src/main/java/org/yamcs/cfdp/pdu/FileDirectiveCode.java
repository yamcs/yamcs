package org.yamcs.cfdp.pdu;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum FileDirectiveCode {
    RESERVED((byte) 0x00),
    EOF((byte) 0x04),
    FINISHED((byte) 0x05),
    ACK((byte) 0x06),
    METADATA((byte) 0x07),
    NAK((byte) 0x08),
    PROMPT((byte) 0x09),
    KEEP_ALIVE((byte) 0x0C);

    private byte code;

    public static final Map<Byte, FileDirectiveCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(FileDirectiveCode.values()),
            FileDirectiveCode::getCode);

    private FileDirectiveCode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    /**
     * returns the FileDirectiveCode for the given code or null if it is not defined
     */
    public static FileDirectiveCode fromCode(byte code) {
        return Lookup.get(code);
    }

    // In the ACK packets, the ACKed packet File Directive is present as the first half of a byte
    public static FileDirectiveCode readFileDirectiveCodeFromHalfAByte(byte b) {
        return FileDirectiveCode.fromCode((byte) ((b & 0xf0) >> 4));
    }

}
