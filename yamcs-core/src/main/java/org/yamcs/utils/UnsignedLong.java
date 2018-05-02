package org.yamcs.utils;

public class UnsignedLong {
    /**
     * Converts unsigned long to double 
     * copied from guava 
     **/
    public static double toDouble(long x) {
        double d = (double) (x & 0x7fffffffffffffffL);
        if (x < 0) {
            d += 0x1.0p63;
        }
        return d;
    }

}
