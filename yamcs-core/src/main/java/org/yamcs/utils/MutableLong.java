package org.yamcs.utils;

public class MutableLong {
    long v;
    public MutableLong(long v) {
        this.v = v;
    }
    public long getLong() {
        return v;
    };
    public void setLong(long v) {
        this.v = v;
    }

    public void increment() {
        v++;
    }
}
