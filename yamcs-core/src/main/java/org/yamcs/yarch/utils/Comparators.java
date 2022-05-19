package org.yamcs.yarch.utils;


/**
 * comparators for yarch data dealing also with nulls and numbers (i.e can compare Float with Integer)
 * 
 * @author nm
 *
 */
public class Comparators {

    public static int compare(Comparable v1, Comparable v2) {
        if (v1 == null) {
            return v2 == null ? 0 : -1;
        } else if (v2 == null) {
            return 1;
        }

        if (v1 instanceof Number && v2 instanceof Number) {
            return compareNumbers((Number) v1, (Number) v2);
        } else {
            return v1.compareTo(v2);
        }
    }

    private static int compareNumbers(Number v1, Number v2) {
        if ((v1 instanceof Float || v1 instanceof Double) && (v2 instanceof Float || v2 instanceof Double)) {
            return Double.compare(v1.doubleValue(), v2.doubleValue());
        } else {
            return Long.compare(v1.longValue(), v2.longValue());
        }
    }
}
