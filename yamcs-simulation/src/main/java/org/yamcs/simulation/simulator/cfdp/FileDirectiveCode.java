package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum FileDirectiveCode {
    Reserved(0x00),
    EOF(0x04),
    Finished(0x05),
    ACK(0x06),
    Metadata(0x07),
    NAK(0x08),
    Prompt(0x09),
    KeepAlive(0x0C);

    private int code;

    public static final Map<Integer, FileDirectiveCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(FileDirectiveCode.values()),
            FileDirectiveCode::getCode);

    private FileDirectiveCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    private static FileDirectiveCode fromCode(int code) {
        if (Lookup.containsKey(code)) {
            return Lookup.get(code);
        } else {
            return Reserved;
        }
    }

    public static FileDirectiveCode readFileDirectiveCode(ByteBuffer buffer) {
        return readFileDirectiveCode(buffer.get());
    }

    public static FileDirectiveCode readFileDirectiveCode(byte b) {
        return FileDirectiveCode.fromCode(b >> 4);
    }

}
