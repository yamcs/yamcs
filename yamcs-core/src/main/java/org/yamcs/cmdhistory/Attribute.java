package org.yamcs.cmdhistory;

import org.yamcs.parameter.Value;

public class Attribute {
    final String key;
    final Value value;

    public Attribute(String key, Value value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public Value getValue() {
        return value;
    }

    public String toString() {
        return key + ": " + value;
    }
}
