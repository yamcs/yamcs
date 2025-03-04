package org.yamcs.utils;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

public class IntHashSet implements Iterable<Integer> {
    private int[] table;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;
    private static final int EMPTY = Integer.MIN_VALUE;
    private static final int DELETED = Integer.MAX_VALUE; // Marker for deleted slots
    private boolean containsMinValue;

    /**
     * Constructs a new IntHashSet and initializes it with values from the given IntArray.
     *
     * @param a
     *            the IntArray containing initial values for the set
     */
    public IntHashSet(IntArray a) {
        table = new int[a.size() * 2];
        Arrays.fill(table, EMPTY);
        size = 0;
        containsMinValue = false;
        for (int i = 0; i < a.size(); i++) {
            add(a.get(i));
        }
    }

    /**
     * Constructs an empty IntHashSet with the default capacity.
     */
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
            if (value != EMPTY && value != DELETED) {
                add(value);
            }
        }
    }

    /**
     * Adds a value to the set if it is not already present.
     *
     * @param value
     *            the value to be added
     * @return true if the value was added, false if it was already present
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
        while (table[index] != EMPTY && table[index] != DELETED) {
            if (table[index] == value) {
                return false; // Duplicate found
            }
            index = (index + 1) % table.length;
        }

        table[index] = value;
        size++;
        return true;
    }

    /**
     * Checks whether the set contains the given value.
     *
     * @param value
     *            the value to check for presence in the set
     * @return true if the value is present, false otherwise
     */
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

    /**
     * Removes the specified value from the set if it exists.
     *
     * @param value
     *            the value to remove
     * @return true if the set contained the value and it was removed, false otherwise
     */
    public boolean remove(int value) {
        if (value == EMPTY) {
            if (containsMinValue) {
                containsMinValue = false;
                size--;
                return true;
            }
            return false;
        }

        int index = hash(value);
        while (table[index] != EMPTY) {
            if (table[index] == value) {
                table[index] = DELETED;
                size--;
                return true;
            }
            index = (index + 1) % table.length;
        }
        return false;
    }

    /**
     * Returns the number of elements currently in the set.
     *
     * @return the number of elements in the set
     */
    public int size() {
        return size;
    }

    /**
     * Returns an iterator over the elements in this set.
     *
     * @return an iterator for iterating over the set's values
     */
    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new IntHashSetIterator();
    }

    private class IntHashSetIterator implements PrimitiveIterator.OfInt {
        private int index = 0;
        private boolean minValueReturned = !containsMinValue;

        @Override
        public boolean hasNext() {
            if (!minValueReturned) {
                return true;
            }
            while (index < table.length) {
                if (table[index] != EMPTY && table[index] != DELETED) {
                    return true;
                }
                index++;
            }
            return false;
        }

        @Override
        public int nextInt() {
            if (!minValueReturned) {
                minValueReturned = true;
                return EMPTY; // Special case for EMPTY (Integer.MIN_VALUE)
            }
            while (index < table.length) {
                int value = table[index++];
                if (value != EMPTY && value != DELETED) {
                    return value;
                }
            }
            throw new NoSuchElementException();
        }
    }

    /**
     * Creates and returns a copy of this IntHashSet.
     *
     * @return a new IntHashSet containing the same elements as this set
     */
    public IntHashSet clone() {
        IntHashSet r = new IntHashSet();
        r.size = size;
        r.table = Arrays.copyOf(table, table.length);
        r.containsMinValue = containsMinValue;
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;

        if (containsMinValue) {
            sb.append(EMPTY);
            first = false;
        }

        for (int value : table) {
            if (value != EMPTY && value != DELETED) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(value);
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
