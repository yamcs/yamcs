package org.yamcs.utils;

import java.util.Arrays;

/**
 * an array that stores the bits in a long[] - each long stores 64 values
 *
 */
public class BooleanArray {
    public static int DEFAULT_CAPACITY = 5;
    private int length = 0;
    private long[] a;

    public BooleanArray() {
        a = new long[DEFAULT_CAPACITY];
    }

    public BooleanArray(int length) {
        a = new long[idx(length) + 1];
    }

    /**
     * 
     * 
     * @param a
     * @param length
     */
    private BooleanArray(long[] a, int length) {
        this.a = a;
        this.length = length;
    }

    /**
     * Inserts the given value in the specified position in the array. Shift all the existing elements at position and
     * the subsequent ones to the right
     * 
     * @param pos
     * @param b
     */
    public void add(int pos, boolean b) {
        if (pos > length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }
        ensureCapacity(length + 1);

        if (pos < length) { // shift all bits to the right
            int idxpos = idx(pos);
            long u = a[idxpos];
            long co = u >>> 63;
            long mask = -1L >>> pos;
            long v = u & mask;
            u &= ~mask;
            a[idxpos] = (v << 1) | u;

            int idxlast = 1 + idx(length + 1);
            for (int i = idxpos + 1; i < idxlast; i++) {
                long t = a[i] >>> 63;
                a[i] = co | (a[i] << 1);
                co = t;
            }
        }
        length++;
        if (b) {
            set(pos);
        } else {
            clear(pos);
        }
    }

    public long[] toLongArray() {
        return Arrays.copyOf(a, idx(length) + 1);
    }

    private void set(int pos) {
        int idx = idx(pos);
        a[idx] |= (1L << pos);
    }

    private void clear(int pos) {
        int idx = idx(pos);
        a[idx] &= ~(1L << pos);
    }

    private void ensureCapacity(int minBitCapacity) {
        int minCapacity = idx(minBitCapacity) + 1;
        if (minCapacity <= a.length) {
            return;
        }

        int capacity = a.length;
        int newCapacity = capacity + (capacity >> 1);

        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }

        a = Arrays.copyOf(a, newCapacity);
    }

    private static int idx(int pos) {
        return pos >> 6;
    }

    private void rangeCheck(int pos) {
        if (pos >= length)
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
    }

    /**
     * Get value on position pos
     * 
     * @param pos
     * @return
     */
    public boolean get(int pos) {
        rangeCheck(pos);
        int idx = idx(pos);
        return ((a[idx] & (1L << pos)) != 0);
    }

    public int size() {
        return length;
    }

    /**
     * Add value at the end of the array
     * 
     * @param b
     */
    public void add(boolean b) {
        ensureCapacity(length + 1);
        if (b) {
            set(length);
        } else {
            clear(length);
        }
        length++;
    }

    /**
     * Create a BooleanArray from the given
     */
    public static BooleanArray valueOf(long[] a, int length) {
        return new BooleanArray(Arrays.copyOf(a, idx(length) + 1), length);
    }
}
