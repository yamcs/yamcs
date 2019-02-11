package org.yamcs.simulation.simulator.cfdp;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum HandlerCode {

    Reserved(0),
    NoticeOfCancellation(1),
    NoticeOfSuspension(2),
    IgnoreError(3),
    AbandonTransaction(4);

    private int code;

    public static final Map<Integer, HandlerCode> Lookup = Maps.uniqueIndex(
            Arrays.asList(HandlerCode.values()),
            HandlerCode::getCode);

    private HandlerCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    private static HandlerCode fromCode(int code) {
        if (Lookup.containsKey(code)) {
            return Lookup.get(code);
        } else {
            return Reserved;
        }
    }

    public static HandlerCode readHandlerCode(byte b) {
        return HandlerCode.fromCode(b & 0x0f);
    }

}
