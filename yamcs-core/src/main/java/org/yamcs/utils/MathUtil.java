package org.yamcs.utils;

/**
 * Provides some of the operations that are required for XTCE MathOperation but are not part of standard java Math.
 * 
 * @author nm
 *
 */
public class MathUtil {
    public static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1.0));
    }

    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1.0));
    }

    public static double atanh(double x) {
        return 0.5 * Math.log((x + 1.0) / (x - 1.0));
    }

    public static double factorial(double v) {
        if (v < 0) {
            throw new IllegalArgumentException("Value must be positive");
        }
        double result = 1;
        for (int i = 2; i <= v; i++) {
            result = result * i;
        }

        return result;
    }

}
