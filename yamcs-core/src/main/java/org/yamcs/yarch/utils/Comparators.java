package org.yamcs.yarch.utils;

import java.util.UUID;

import org.yamcs.time.Instant;

/**
 * comparators for yarch data dealing also with nulls
 * 
 * @author nm
 *
 */
public class Comparators {
    public static int compare(UUID v1, UUID v2) {
        if (v1 == null) {
            return v2==null?0:-1;
        } else if (v2 == null) {
            return 1;
        } else {
            return v1.compareTo(v2);
        }
    }

    public static int compare(String v1, String v2) {
        if (v1 == null) {
            return v2 == null ? 0 : -1;
        } else if (v2 == null) {
            return 1;
        } else {
            return v1.compareTo(v2);
        }
    }

    public static int compare(Instant v1, Instant v2) {
        if (v1 == null) {
            return v2 == null ? 0 : -1;
        } else if (v2 == null) {
            return 1;
        } else {
            return v1.compareTo(v2);
        }
    }
}
