package org.yamcs.utils;
import java.util.Arrays;

public class IntHashSet {
    private int[] table;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;
    private static final int EMPTY = Integer.MIN_VALUE;
    private boolean containsMinValue;

    public IntHashSet() {
        table = new int[DEFAULT_CAPACITY];
        Arrays.fill(table, EMPTY);
        size = 0;
        containsMinValue = false;
    }

    private int hash(int value) {
        return (value & 0x7FFFFFFF) % table.length;
    }

    private void rehash() {
        int[] oldTable = table;
        table = new int[oldTable.length * 2];
        Arrays.fill(table, EMPTY);
        size = 0;
        containsMinValue = false;

        for (int value : oldTable) {
            if (value != EMPTY) {
                add(value);
            }
        }
    }

    /**
     * adds an element to the set and returns true if it has been added.
     * <p>
     * false is returned if the element was already in the set
     */
    public boolean add(int value) {
        if (value == EMPTY) {
            if (containsMinValue) {
                return false;
            } else {
                containsMinValue = true;
                size++;
                return true;
            }
        }

        if (size >= table.length / 2) {
            rehash();
        }

        int index = hash(value);
        while (table[index] != EMPTY) {
            if (table[index] == value) {
                return false; // Duplicate found
            }
            index = (index + 1) % table.length;
        }

        table[index] = value;
        size++;
        return true;
    }

    public boolean contains(int value) {
        if (value == EMPTY) {
            return containsMinValue;
        }

        int index = hash(value);
        while (table[index] != EMPTY) {
            if (table[index] == value) {
                return true;
            }
            index = (index + 1) % table.length;
        }
        return false;
    }
}
