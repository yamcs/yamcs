package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum ActionCode {

    CreateFile(0, false),
    DeleteFile(1, false),
    RenameFile(2, true),
    AppendFile(3, true),
    ReplaceFile(4, true),
    CreateDirectory(5, false),
    RemoveDirectory(6, false),
    DenyFile(7, false),
    DenyDirectory(8, false);

    private int code;
    private boolean secondFileNamePresent;

    public static final Map<Integer, ActionCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(ActionCode.values()),
            ActionCode::getCode);

    private ActionCode(int code, boolean secondFileNamePresent) {
        this.code = code;
        this.secondFileNamePresent = secondFileNamePresent;
    }

    public int getCode() {
        return this.code;
    }

    public boolean hasSecondFileName() {
        return this.secondFileNamePresent;
    }

    private static ActionCode fromCode(int code) {
        if (Lookup.containsKey(code)) {
            return Lookup.get(code);
        } else {
            // TODO
        }
        return null;
    }

    public static ActionCode readActionCode(ByteBuffer buffer) {
        return readActionCode(buffer.get());
    }

    public static ActionCode readActionCode(byte b) {
        return ActionCode.fromCode(b >> 4);
    }
}
