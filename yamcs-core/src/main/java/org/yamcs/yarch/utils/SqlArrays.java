package org.yamcs.yarch.utils;

import java.util.List;
import java.util.Objects;

public class SqlArrays {
    /**
     * returns true if the two lists have at least one common element
     */
    static public boolean overlap(List<Object> l1, List<Object> l2) {
        for (Object o1 : l1) {
            for (Object o2 : l2) {
                if (Objects.equals(o1, o2)) {
                    return true;
                }
            }
        }
        return false;
    }
}
