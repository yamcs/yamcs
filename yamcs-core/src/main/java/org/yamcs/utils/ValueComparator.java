package org.yamcs.utils;

import java.util.Comparator;

import org.yamcs.protobuf.Yamcs.Value;

public class ValueComparator implements Comparator<Value> {

    @Override
    public int compare(Value a, Value b) {
        return ValueUtility.compare(a, b);
    }
}
