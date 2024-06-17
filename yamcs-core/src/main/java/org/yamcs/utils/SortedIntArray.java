package org.yamcs.utils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;

/**
 * sorted int array
 * 
 * 
 * @author nm
 *
 */
public class SortedIntArray implements Serializable {
    static final long serialVersionUID = 1L;

    public static int DEFAULT_CAPACITY = 10;
    private int[] a;
    private int length;

    // caches the hashCode
    private int hash;

    /**
     * Creates a sorted int array with a default initial capacity
     * 
     */
    public SortedIntArray() {
        a = new int[DEFAULT_CAPACITY];
    }

    /**
     * Creates a sorted int array with a given initial capacity
     * 
     * @param capacity
     */
    public SortedIntArray(int capacity) {
        a = new int[capacity];
    }

    /**
     * Creates the SortedIntArray by copying all values from the input array and sorting them
     * 
     * @param array
     */
    public SortedIntArray(int... array) {
        length = array.length;
        a = Arrays.copyOf(array, length);
        Arrays.sort(a);
    }

    public SortedIntArray(IntArray pids) {
        length = pids.size();
        a = Arrays.copyOf(pids.array(), length);
        Arrays.sort(a);
    }

    /**
     * Inserts value to the array and return the position on which has been inserted.
     * <p>
     * In case <code>x</code> is already present in the array, this function inserts the new value at a position after
     * the values already present
     * 
     * @param x
     *            - value to be inserted
     * @return the position on which the value has been inserted
     */
    public int insert(int x) {

        int pos = Arrays.binarySearch(a, 0, length, x);
        if (pos < 0) {
            pos = -pos - 1;
        } else { // make sure we insert after the last value
            while (pos < length && a[pos] == x) {
                pos++;
            }
        }

        ensureCapacity(length + 1);

        System.arraycopy(a, pos, a, pos + 1, length - pos);
        a[pos] = x;
        length++;
        hash = 0;
        return pos;
    }

    /**
     * performs a binary search in the array.
     * 
     * @see java.util.Arrays#binarySearch(int[], int)
     * @param x
     * @return result of the binarySearch, @see java.util.Arrays#binarySearch(int[], int)
     */
    public int search(int x) {
        return Arrays.binarySearch(a, 0, length, x);
    }

    /**
     * returns idx such that
     * 
     * <pre>
     * a[i] >= x iif i >= idx
     * </pre>
     * 
     */
    public int lowerBound(int x) {
        int idx = Arrays.binarySearch(a, 0, length, x);
        if (idx < 0) {
            return -(idx + 1);
        } else {
            while (idx > 0 && a[idx - 1] == x) {
                idx--;
            }
        }
        return idx;
    }

    /**
     * returns idx such that
     * 
     * <pre>
     * a[i] <= x iif i <= idx
     * </pre>
     */
    public int higherBound(int x) {
        int idx = Arrays.binarySearch(a, 0, length, x);
        if (idx < 0) {
            return -(idx + 1) - 1;
        } else {
            while (idx < length - 1 && a[idx + 1] == x) {
                idx++;
            }
            return idx;
        }
    }

    /**
     * get element at position
     * 
     * @param pos
     * @return the element at position
     */
    public int get(int pos) {
        if (pos >= length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }

        return a[pos];
    }

    private void ensureCapacity(int minCapacity) {
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

    public boolean isEmpty() {
        return length == 0;
    }

    public int[] getArray() {
        return Arrays.copyOf(a, length);
    }

    public int size() {
        return length;
    }

    /**
     * Constructs an ascending iterator starting from a specified value (inclusive)
     * 
     * @param startFrom
     * @return an iterator starting from the specified value
     */
    public PrimitiveIterator.OfInt getAscendingIterator(int startFrom) {
        return new PrimitiveIterator.OfInt() {
            int pos;
            {
                pos = search(startFrom);
                if (pos < 0) {
                    pos = -pos - 1;
                }
            }

            @Override
            public boolean hasNext() {
                return pos < length;
            }

            @Override
            public int nextInt() {
                return a[pos++];

            }
        };
    }

    /**
     * Constructs an descending iterator starting from a specified value (exclusive)
     * 
     * @param startFrom
     * @return an descending iterator starting from the specified value
     */
    public PrimitiveIterator.OfInt getDescendingIterator(int startFrom) {
        return new PrimitiveIterator.OfInt() {
            int pos;
            {
                pos = search(startFrom);
                if (pos < 0) {
                    pos = -pos - 1;
                }
                pos--;
            }

            @Override
            public boolean hasNext() {
                return pos >= 0;
            }

            @Override
            public int nextInt() {
                return a[pos--];

            }
        };
    }

    public void forEach(IntConsumer action) {
        for (int i = 0; i < length; i++) {
            action.accept(a[i]);
        }
    }

    /**
     * Performs a binary search and returns true if this array contains the value.
     * 
     * @param x
     *            - value to check
     * @return true of the array contains the specified value
     * 
     */
    public boolean contains(int x) {
        return Arrays.binarySearch(a, 0, length, x) >= 0;
    }

    public static SortedIntArray decodeFromVarIntArray(byte[] buf) {
        if (buf.length == 0) {
            return new SortedIntArray(0);
        }
        SortedIntArray sia = new SortedIntArray();

        VarIntUtil.ArrayDecoder ad = VarIntUtil.newArrayDecoder(buf);
        int s = 0;
        while (ad.hasNext()) {
            s += ad.next();
            sia.insert(s);
        }
        return sia;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        SortedIntArray other = (SortedIntArray) obj;
        if (length != other.length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (a[i] != other.a[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Change the elements of the array by adding x to each element
     */
    public void addToAll(int x) {
        for (int i = 0; i < length; i++) {
            a[i] += x;
        }
    }

    /**
     * Add x to the elements of the array whose value is greater than v
     */
    public void addIfGreaterThan(int v, int x) {
        for (int i = length - 1; i >= 0; i--) {
            if (a[i] > v) {
                a[i] += x;
            } else {
                break;
            }
        }
    }

    /**
     * Add x to the elements of the array whose value is greater or equal than v
     */
    public void addIfGreaterOrEqualThan(int v, int x) {
        for (int i = length - 1; i >= 0; i--) {
            if (a[i] >= v) {
                a[i] += x;
            } else {
                break;
            }
        }
    }

    @Override
    public String toString() {
        if (length == 0) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        int n = length - 1;

        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == n) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && length > 0) {
            h = 1;

            for (int i = 0; i < length; i++) {
                h = 31 * h + a[i];
            }
            hash = h;
        }
        return h;
    }

}
