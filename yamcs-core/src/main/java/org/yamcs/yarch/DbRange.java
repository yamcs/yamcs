package org.yamcs.yarch;

import org.yamcs.utils.StringConverter;

public class DbRange {
    public byte[] rangeStart = null;
    public byte[] rangeEnd = null;

    @Override
    public String toString() {
        return "DbRange [rangeStart=" + StringConverter.arrayToHexString(rangeStart) + ", rangeEnd="
                + StringConverter.arrayToHexString(rangeEnd) + "]";
    }
}
