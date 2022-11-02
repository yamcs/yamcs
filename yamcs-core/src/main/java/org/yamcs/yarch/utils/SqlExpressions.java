package org.yamcs.yarch.utils;

import java.util.Arrays;

public class SqlExpressions {
    public static Object EQUAL(Object v1, Object v2) {
        if (v1 == null || v2 == null) {
            return null;
        }

        if (v1 instanceof Number && v2 instanceof Number) {
            return compareNumbers((Number) v1, (Number) v2) == 0;
        } else {
            return v1.equals(v2);
        }
    }

    public static Object NOT_EQUAL(Object v1, Object v2) {
        if (v1 == null || v2 == null) {
            return null;
        }

        if (v1 instanceof Number && v2 instanceof Number) {
            return compareNumbers((Number) v1, (Number) v2) != 0;
        } else {
            return !v1.equals(v2);
        }
    }

    public static Object GREATER_OR_EQUAL(Comparable v1, Comparable v2) {
        if (v1 == null || v2 == null) {
            return null;
        }

        if (v1 instanceof Number && v2 instanceof Number) {
            return compareNumbers((Number) v1, (Number) v2) >= 0;
        } else {
            return v1.compareTo(v2) >= 0;
        }
    }

    public static Object GREATER(Comparable v1, Comparable v2) {
        if (v1 == null || v2 == null) {
            return null;
        }

        if (v1 instanceof Number && v2 instanceof Number) {
            return compareNumbers((Number) v1, (Number) v2) > 0;
        } else {
            return v1.compareTo(v2) > 0;
        }
    }

    public static Object LESS_OR_EQUAL(Comparable v1, Comparable v2) {
        if (v1 == null || v2 == null) {
            return null;
        }

        if (v1 instanceof Number && v2 instanceof Number) {
            return compareNumbers((Number) v1, (Number) v2) <= 0;
        } else {
            return v1.compareTo(v2) <= 0;
        }
    }

    public static Object LESS(Comparable v1, Comparable v2) {
        if (v1 == null || v2 == null) {
            return null;
        }

        if (v1 instanceof Number && v2 instanceof Number) {
            return compareNumbers((Number) v1, (Number) v2) < 0;
        } else {
            return v1.compareTo(v2) < 0;
        }
    }


    private static int compareNumbers(Number v1, Number v2) {
        if ((v1 instanceof Float || v1 instanceof Double) && (v2 instanceof Float || v2 instanceof Double)) {
            return Double.compare(v1.doubleValue(), v2.doubleValue());
        } else {
            return Long.compare(v1.longValue(), v2.longValue());
        }
    }

    public static Object AND(Object... values) {
        boolean r = true;
        for (Object o : values) {
            if (o == null) {
                return null;
            }
            if (o instanceof Boolean) {
                r = r & (Boolean) o;
            } else {
                throw new IllegalStateException("Illegal value in AND " + o);
            }
        }
        return r;
    }

    public static Object OR(Object... values) {
        boolean r = false;
        for (Object o : values) {
            if (o == null) {
                return null;
            }
            if (o instanceof Boolean) {
                r = r | (Boolean) o;
            } else {
                throw new IllegalStateException("Illegal value in OR " + o);
            }
        }
        return r;
    }
}
