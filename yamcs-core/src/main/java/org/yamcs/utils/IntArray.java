package org.yamcs.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * int array
 * 
 * 
 * @author nm
 *
 */
public class IntArray {

    public static int DEFAULT_CAPACITY = 10;
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
     *            - value to be added
     */
    public void add(int x) {
        ensureCapacity(length + 1);
        a[length] = x;
        length++;
    }

    public void add(int pos, int x) {
        if (pos > length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }
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

    public int size() {
        return length;
    }

    public void set(int pos, int x) {
        rangeCheck(pos);
        a[pos] = x;
    }

    private void rangeCheck(int pos) {
        if (pos >= length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }
    }

    /**
     * Returns the index of the first occurrence of the specified element in the
     * array, or -1 if the array does not contain the element.
     * 
     * @param x
     *            element which is searched for
     * 
     * @return the index of the first occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element.
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
     * Sort the array concurrently swapping the elements in the list such that the
     * correspondence is kept.
     * 
     * The list has to contain the same number of elements as the array
     * 
     * @param list
     */
    public void sort(List<?> list) {
        if (list.size() != length) {
            throw new IllegalArgumentException("The list has not the same number of elements as the array");
        }
        quickSort(0, length - 1, list);
    }

    private void quickSort(int lo, int hi, List<?> list) {
        while (lo < hi) {
            int pi = partition(lo, hi, list);
            if (pi - lo < hi - pi) {
                quickSort(lo, pi - 1, list);
                lo = pi + 1;
            } else {
                quickSort(pi + 1, hi, list);
                hi = pi - 1;
            }
        }
    }

    public int count = 0;

    private int partition(int lo, int hi, List<?> list) {
        int pivot = a[hi];
        int i = lo - 1;

        for (int j = lo; j < hi; j++) {
            count++;
            if (a[j] < pivot) {
                i++;
                swap(i, j, list);
            }
        }

        swap(i + 1, hi, list);

        return i + 1;
    }

    private void swap(int i, int j, List<?> list) {
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
        Collections.swap(list, i, j);
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

}
