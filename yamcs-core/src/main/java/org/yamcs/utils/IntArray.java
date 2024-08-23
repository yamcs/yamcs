package org.yamcs.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

/**
 * expandable array of ints
 * 
 */
public class IntArray implements Iterable<Integer> {
    public static final int DEFAULT_CAPACITY = 10;
    private int[] a;
    private int length;

    // caches the hashCode
    private int hash;

    /**
     * Creates a sorted int array with a default initial capacity
     */
    public IntArray() {
        a = new int[DEFAULT_CAPACITY];
    }

    /**
     * Creates an IntArray with a given initial capacity
     * 
     * @param capacity
     */
    public IntArray(int capacity) {
        a = new int[capacity];
    }

    private IntArray(int[] a1) {
        a = a1;
        length = a1.length;
    }

    /**
     * Creates the IntArray with the backing array
     * 
     * @param array
     * @return a new object containing all the values from the passed array
     */
    public static IntArray wrap(int... array) {
        return new IntArray(array);
    }

    /**
     * add value to the array
     * 
     * @param x
     *            * - value to be added
     */
    public void add(int x) {
        hash = 0;
        ensureCapacity(length + 1);
        a[length] = x;
        length++;
    }

    public void add(int pos, int x) {
        if (pos > length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }
        hash = 0;
        ensureCapacity(length + 1);
        System.arraycopy(a, pos, a, pos + 1, length - pos);
        a[pos] = x;
        length++;
    }

    /**
     * get element at position
     * 
     * @param pos
     * @return the element at the specified position
     */
    public int get(int pos) {
        rangeCheck(pos);

        return a[pos];
    }

    /**
     * Remove element at position shifting all subsequent elements to the left
     * 
     * @param pos
     * @return the element removed
     */
    public int remove(int pos) {
        rangeCheck(pos);
        hash = 0;
        int r = a[pos];

        System.arraycopy(a, pos + 1, a, pos, length - pos - 1);
        length--;
        return r;

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

    public IntStream stream() {
        return Arrays.stream(a, 0, length);
    }

    public boolean isEmpty() {
        return a.length == 0;
    }

    public int[] toArray() {
        return Arrays.copyOf(a, length);
    }

    /**
     * @return the size of the array (which is smaller or equal than the length of the underlying int[] array)
     */
    public int size() {
        return length;
    }

    public void set(int pos, int x) {
        rangeCheck(pos);
        hash = 0;
        a[pos] = x;
    }

    private void rangeCheck(int pos) {
        if (pos >= length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }
    }

    /**
     * Returns the index of the first occurrence of the specified element in the array, or -1 if the array does not
     * contain the element.
     * 
     * @param x
     *            element which is searched for
     * 
     * @return the index of the first occurrence of the specified element in this list, or -1 if this list does not
     *         contain the element.
     */
    public int indexOf(int x) {
        for (int i = 0; i < length; i++) {
            if (a[i] == x)
                return i;
        }
        return -1;
    }

    /**
     * get the backing array
     * 
     * @return the backing array
     */
    public int[] array() {
        return a;
    }

    /**
     * Assuming that the array is sorted, performs a binary search and returns the position of the found element.
     * 
     * See {@link Arrays#binarySearch(int[], int)} for details.
     * 
     * If the array is not sorted, the behaviour is undefined.
     * 
     * @param x
     *            - the value to be searched for
     * @return
     */
    public int binarySearch(int x) {
        return Arrays.binarySearch(a, 0, length, x);
    }

    /**
     * return the number of elements of the intersection of the two arrays
     * <p>
     * both this and input have to be sorted
     */
    public int intersectionSize(IntArray input) {
        int count = 0;
        int idx1 = 0;
        int idx2 = 0;

        while (idx1 < this.length && idx2 < input.length) {
            if (this.a[idx1] < input.a[idx2]) {
                idx1++;
            } else if (this.a[idx1] > input.a[idx2]) {
                idx2++;
            } else {
                count++;
                idx1++;
                idx2++;
            }
        }

        return count;
    }

    /**
     * Assuming that a1 and a2 are sorted, returns a new array storing the union.
     * <p>
     * sizeHint is the expected size of the returned array
     */
    public static IntArray union(IntArray a1, IntArray a2, int sizeHint) {
        IntArray union = new IntArray(sizeHint);
        int idx1 = 0;
        int idx2 = 0;

        while (idx1 < a1.length && idx2 < a2.length) {
            if (a1.a[idx1] < a2.a[idx2]) {
                union.add(a1.a[idx1]);
                idx1++;
            } else if (a1.a[idx1] > a2.a[idx2]) {
                union.add(a2.a[idx2]);
                idx2++;
            } else {
                union.add(a1.a[idx1]);
                idx1++;
                idx2++;
            }
        }
        while (idx1 < a1.length) {
            union.add(a1.a[idx1]);
            idx1++;
        }

        while (idx2 < a2.length) {
            union.add(a2.a[idx2]);
            idx2++;
        }
        return union;
    }

    public void sort() {
        Arrays.sort(a, 0, length);
        hash = 0;
    }

    /**
     * Sort the array concurrently swapping the elements in the list such that the correspondence is kept.
     * 
     * The list has to contain the same number of elements as the array
     * 
     * @param list
     */
    public void sort(List<?> list) {
        if (list.size() != length) {
            throw new IllegalArgumentException("The list has not the same number of elements as the array");
        }
        if (length == 0) {
            return;
        }
        quickSort(0, length - 1, list);
    }

    private void quickSort(int lo, int hi, List<?> list) {
        int pi = partition(lo, hi, list);
        if (lo < pi - 1) {
            quickSort(lo, pi - 1, list);
        }
        if (pi < hi) {
            quickSort(pi, hi, list);
        }
    }

    private int partition(int lo, int hi, List<?> list) {
        int pivot = a[(lo + hi) >>> 1];

        int i = lo, j = hi;

        while (i <= j) {
            while (a[i] < pivot)
                i++;

            while (a[j] > pivot)
                j--;

            if (i <= j) {
                swap(i, j, list);
                i++;
                j--;
            }
        }

        return i;
    }

    private void swap(int i, int j, List<?> list) {
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
        Collections.swap(list, i, j);
        hash = 0;
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        IntArray other = (IntArray) obj;
        if (length != other.length)
            return false;

        for (int i = 0; i < length; i++) {
            if (a[i] != other.a[i])
                return false;
        }

        return true;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        int n = length - 1;
        if (n == -1) {
            return "[]";
        }
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == n)
                return b.append(']').toString();
            b.append(", ");
        }
    }


    /**
     * Compares two arrays. Assuming that the arrays a1 and a2 are sorted, it returns
     * <ul>
     * <li>0 if a1 == a2</li>
     * <li>1 if a1 is a subset of a2</li>
     * <li>2 if a2 is a subset of a1</li>
     * <li>-1 otherwise</li>
     * </ul>
     * 
     * If the arrays are not sorted the return is meaningless.
     * 
     * @param a1
     *            - first array
     * @param a2
     *            - second array
     */
    public static int compare(IntArray a1, IntArray a2) {
        int i1 = 0;
        int i2 = 0;
        int c = 0;
        while (i1 < a1.size() && i2 < a2.size()) {
            int x1 = a1.get(i1);
            int x2 = a2.get(i2);
            if (x1 == x2) {
                i1++;
                i2++;
                continue;
            }
            if (x1 > x2) {
                if (c == 0) {
                    c = 1;
                } else if (c == 2) {
                    c = -1;
                    break;
                }
                i2++;
            } else { // x1 < x2
                if (c == 0) {
                    c = 2;
                } else if (c == 1) {
                    c = -1;
                    break;
                }
                i1++;
            }
        }

        if (c != -1) {
            if (i1 < a1.size()) {
                c = (c == 0) ? 2 : -1;
            } else if (i2 < a2.size()) {
                c = (c == 0) ? 1 : -1;
            }
        }
        return c;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new IntArrayIterator();
    }

    private class IntArrayIterator implements PrimitiveIterator.OfInt {
        private int idx = 0;

        @Override
        public boolean hasNext() {
            return idx < length;
        }

        @Override
        public int nextInt() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return a[idx++];
        }
    }
}
