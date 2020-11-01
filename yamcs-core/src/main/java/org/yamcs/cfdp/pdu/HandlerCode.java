package org.yamcs.cfdp.pdu;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public enum HandlerCode {

    RESERVED(0),
    NOTICE_OF_CANCELLATION(1),
    NOTICE_OF_SUSPENSION(2),
    IGNORE_ERROR(3),
    ABANDON_TRANSACTION(4);

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
            return RESERVED;
        }
    }

    public static HandlerCode readHandlerCode(byte b) {
        return HandlerCode.fromCode(b & 0x0f);
    }

}
