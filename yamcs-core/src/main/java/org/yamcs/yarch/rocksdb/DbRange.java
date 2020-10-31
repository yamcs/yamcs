package org.yamcs.yarch.rocksdb;


import org.yamcs.utils.StringConverter;

class DbRange {
   
    byte[] rangeStart = null;
    boolean strictStart = false;
    byte[] rangeEnd = null;
    boolean strictEnd = false;

    @Override
    public String toString() {
        return "DbRange [rangeStart=" + StringConverter.arrayToHexString(rangeStart) + ", strictStart=" + strictStart + ", rangeEnd="
                + StringConverter.arrayToHexString(rangeEnd) + ", strictEnd=" + strictEnd + "]";
    }
}
