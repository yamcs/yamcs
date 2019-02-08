package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum StatusCode {

    Successful(0x00),
    CreateNotAllowed(0x01),
    NotPerformed(0x0f),
    FileDoesNotExist(0x01),
    DeleteNotAllowed(0x02),
    OldFileNameDoesNotExist(0x01),
    NewFileNameAlreadyExists(0x02),
    RenameNotAllowed(0x03),
    FileName1DoesNotExist(0x01),
    FileName2DoesNotExist(0x02),
    AppendNotAllowed(0x03),
    ReplaceNotAllowed(0x03),
    DirectoryCannotBeCreated(0x01),
    DirectoryDoesNotExist(0x01);

    private int code;

    public static final Map<Integer, StatusCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(StatusCode.values()),
            StatusCode::getCode);

    private StatusCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    private static StatusCode fromCode(int code) {
        if (Lookup.containsKey(code)) {
            return Lookup.get(code);
        } else {
            // TODO
        }
        return null;
    }

    public static StatusCode readStatusCode(ByteBuffer buffer) {
        return readStatusCode(buffer.get());
    }

    public static StatusCode readStatusCode(byte b) {
        return StatusCode.fromCode(b & 0x0f);
    }
}
