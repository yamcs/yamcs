package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum StatusCode {

    Successful((byte) 0x00),
    CreateNotAllowed((byte) 0x01),
    NotPerformed((byte) 0x0f),
    FileDoesNotExist((byte) 0x01),
    DeleteNotAllowed((byte) 0x02),
    OldFileNameDoesNotExist((byte) 0x01),
    NewFileNameAlreadyExists((byte) 0x02),
    RenameNotAllowed((byte) 0x03),
    FileName1DoesNotExist((byte) 0x01),
    FileName2DoesNotExist((byte) 0x02),
    AppendNotAllowed((byte) 0x03),
    ReplaceNotAllowed((byte) 0x03),
    DirectoryCannotBeCreated((byte) 0x01),
    DirectoryDoesNotExist((byte) 0x01);

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
        } else {
            // TODO
        }
        return null;
    }

    public static StatusCode readStatusCode(ByteBuffer buffer) {
        return readStatusCode(buffer.get());
    }

    public static StatusCode readStatusCode(byte b) {
        return StatusCode.fromCode((byte) (b & 0x0f));
    }
}
