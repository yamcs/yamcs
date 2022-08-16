package org.yamcs.yarch.utils;

import java.util.List;
import java.util.Objects;

public class SqlArrays {
    /**
     * Implements SQL array overlap operator
     * <p>
     * Returns true if the two lists have at least one common element
     * <p>
     * If any of the lists is null return false;
     */
    static public boolean overlap(List<Object> l1, List<Object> l2) {
        if (l1 == null || l2 == null) {
            return false;
        }
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
