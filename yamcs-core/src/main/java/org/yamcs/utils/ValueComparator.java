package org.yamcs.utils;

import java.util.Comparator;

import org.yamcs.parameter.Value;

public class ValueComparator implements Comparator<Value> {

    @Override
    public int compare(Value a, Value b) {
        return ValueUtility.compare(a, b);
    }
}
