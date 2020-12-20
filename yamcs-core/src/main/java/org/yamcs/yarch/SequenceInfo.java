package org.yamcs.yarch;

public class SequenceInfo {
    final private String name;
    final private long value;

    public SequenceInfo(String name, long value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public long getValue() {
        return value;
    }
}
