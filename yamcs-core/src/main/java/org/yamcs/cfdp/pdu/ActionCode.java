package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum ActionCode {

    CREATE_FILE((byte) (0x00), false),
    DELETE_FILE((byte) (0x01), false),
    RENAME_FILE((byte) (0x02), true),
    APPEND_FILE((byte) (0x03), true),
    REPLACE_FILE((byte) (0x04), true),
    CREATE_DIRECTORY((byte) (0x05), false),
    REMOVE_DIRECTORY((byte) (0x06), false),
    DENY_FILE((byte) (0x07), false),
    DENY_DIRECTORY((byte) (0x08), false);

    private byte code;
    private boolean secondFileNamePresent;

    public static final Map<Byte, ActionCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(ActionCode.values()),
            ActionCode::getCode);

    private ActionCode(byte code, boolean secondFileNamePresent) {
        this.code = code;
        this.secondFileNamePresent = secondFileNamePresent;
    }

    public byte getCode() {
        return this.code;
    }

    public boolean hasSecondFileName() {
        return this.secondFileNamePresent;
    }

    private static ActionCode fromCode(byte code) {
        if (Lookup.containsKey(code)) {
            return Lookup.get(code);
        }
        throw new IllegalArgumentException("In CFDP Action Code: " + code);
    }

    public static ActionCode readActionCode(ByteBuffer buffer) {
        return readActionCode(buffer.get());
    }

    public static ActionCode readActionCode(byte b) {
        return ActionCode.fromCode((byte) (b >> 4));
    }
}
