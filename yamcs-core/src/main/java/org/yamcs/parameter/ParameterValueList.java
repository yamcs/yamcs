package org.yamcs.parameter;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.yamcs.xtce.Parameter;

/**
 * 
 * Stores a collection of ParameterValue indexed on Parameter
 * <p>
 * it works like a LinkedHashMap&lt;Parameter, LinkedList&lt;ParameterValue&gt;&gt;
 * <p>
 * it also works like a LinkedList&lt;ParameterValue;&gt;
 * <p>
 * Not thread safe
 * 
 */
public class ParameterValueList implements Collection<ParameterValue> {
    static public final ParameterValueList EMPTY = new ParameterValueList();

    Entry[] table;

    Entry head;
    int size;
    int threshold;
    float loadFactor = 0.75f;
    private int rmCount = 0;

    public ParameterValueList() {
        size = 0;
        table = new Entry[16];
        threshold = (int) (table.length * loadFactor);
        initHead();
    }

    /**
     * @param pvs
     */
    public ParameterValueList(Collection<ParameterValue> pvs) {
        int len = (int) (pvs.size() / loadFactor) + 1;
        len = roundUpToPowerOfTwo(len);
        table = new Entry[len];
        threshold = (int) (len * loadFactor);
        size = 0;
        initHead();
        for (ParameterValue pv : pvs) {
            doAdd(pv);
        }
    }

    // used for unit tests to ensure max collision
    ParameterValueList(int capacity, Collection<ParameterValue> pvs) {
        int len = roundUpToPowerOfTwo(capacity);
        table = new Entry[len];
        threshold = (int) (len * loadFactor);
        size = 0;
        initHead();

        for (ParameterValue pv : pvs) {
            doAdd(pv);
        }
    }

    private void initHead() {
        head = new Entry(null);
        head.before = head.after = head;
    }

    @Override
    public boolean add(ParameterValue pv) {
        if (pv == null) {
            throw new NullPointerException();
        }
        if (size - 1 >= threshold) {
            ensureCapacity(2 * table.length);
            threshold = 2 * threshold;
        }
        doAdd(pv);
        return true;
    }

    /**
     * Return the number of values for p
     * 
     * @param p
     * @return
     */
    public int count(Parameter p) {
        int hash = getHash(p);
        int index = hash & (table.length - 1);
        Entry e = table[index];
        int count = 0;

        while (e != null) {
            if (e.pv.getParameter() == p) {
                count++;
            }
            e = e.next;
        }
        return count;
    }

    private void ensureCapacity(int newCapacity) {
        Entry[] oldt = table;
        Entry[] newt = new Entry[newCapacity];

        // transfer content
        for (int i = 0; i < oldt.length; i++) {
            Entry e = oldt[i];
            while (e != null) {
                int hash = getHash(e.pv.getParameter());
                int index = hash & (newt.length - 1);

                Entry next = e.next;
                e.next = null;
                Entry e1 = newt[index];
                if (e1 == null) {
                    newt[index] = e;
                } else {
                    while (e1.next != null) {
                        e1 = e1.next;
                    }
                    e1.next = e;

                }
                e = next;
            }
        }
        table = newt;
    }

    /**
     * add a parameter to the hashtable, to the end of the list for the same parameter
     * 
     * @param pv
     */
    private void doAdd(ParameterValue pv) {
        Entry newEntry = new Entry(pv);
        Entry[] t = table;

        int hash = getHash(pv.getParameter());
        int index = hash & (t.length - 1);
        if (t[index] == null) {
            t[index] = newEntry;
        } else {
            Entry e = t[index];
            while (e.next != null) {
                e = e.next;
            }
            e.next = newEntry;
        }
        newEntry.after = head;
        newEntry.before = head.before;
        head.before.after = newEntry;
        head.before = newEntry;

        size++;
    }

    public ParameterValue getFirst() {
        return head.after.pv;
    }

    public ParameterValue getLast() {
        return head.before.pv;
    }

    private int getHash(Parameter p) {
        return p.hashCode();
    }

    public int getSize() {
        return size;
    }

    /**
     * Returns the last inserted value for Parameter p or null if there is no value
     * 
     * @param p
     * @return
     */
    public ParameterValue getLastInserted(Parameter p) {
        int index = getHash(p) & (table.length - 1);
        ParameterValue r = null;
        for (Entry e = table[index]; e != null; e = e.next) {
            if (e.pv.getParameter() == p) {
                r = e.pv;
            }
        }
        return r;
    }

    /**
     * Returns first inserted parameter value for the given parameter or null if there is none
     * 
     * @param p
     * @return
     */
    public ParameterValue getFirstInserted(Parameter p) {
        int index = getHash(p) & (table.length - 1);
        ParameterValue r = null;
        for (Entry e = table[index]; e != null; e = e.next) {
            if (e.pv.getParameter() == p) {
                r = e.pv;
                break;
            }
        }
        return r;
    }

    /**
     * Returns the n'th instance of the parameter or null if it does not exist
     * <p>
     * If n = 0 it is equivalent with {@link #getFirstInserted(Parameter)}
     * 
     */
    public ParameterValue get(Parameter p, int n) {
        int index = getHash(p) & (table.length - 1);
        ParameterValue r = null;
        for (Entry e = table[index]; e != null; e = e.next) {
            if (e.pv.getParameter() == p) {
                r = e.pv;
                if (n-- == 0) {
                    break;
                }
            }
        }
        return r;
    }

    /**
     * Returns the n'th instance of the parameter from the end or null if it does not exist
     * <p>
     * If n = 0 it is equivalent with {@link #getLastInserted(Parameter)}
     * 
     */
    public ParameterValue getFromEnd(Parameter p, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }

        int index = getHash(p) & (table.length - 1);
        Entry e = table[index];
        Entry lastMatch = null;

        // Traverse the linked list and find the last entry for the given parameter
        while (e != null) {
            if (e.pv.getParameter() == p) {
                lastMatch = e;
            }
            e = e.next;
        }

        if (lastMatch == null) {
            return null; // No entry found for this parameter
        }

        // Now walk backwards from the last match
        Entry current = lastMatch;
        for (int i = 0; i < n; i++) {
            do {
                current = current.before;
            } while (current != head && current.pv.getParameter() != p);

            if (current == head) {
                return null; // Less than n matches
            }
        }

        return current.pv;
    }

    /**
     * Performs the given action for each value of the parameter p The values are considered in insertion order - oldest
     * is first to be processed
     * 
     * @param p
     * @param action
     */
    public void forEach(Parameter p, Consumer<ParameterValue> action) {
        int index = getHash(p) & (table.length - 1);
        for (Entry e = table[index]; e != null; e = e.next) {
            if (e.pv.getParameter() == p) {
                action.accept(e.pv);
            }
        }
    }

    /**
     * Remove the last inserted value for Parameter p
     * 
     * @param p
     * @return the value removed or null if there was no value for p
     */
    public ParameterValue removeLast(Parameter p) {
        int index = getHash(p) & (table.length - 1);
        Entry e = table[index];
        if (e == null) {
            return null;
        }

        Entry prev_r = null;

        Entry prev_e = null;
        Entry r = null;

        while (e != null) {
            if (e.pv.getParameter() == p) {
                prev_r = prev_e;
                r = e;
            }
            prev_e = e;
            e = e.next;
        }

        if (r == null) {
            return null;
        }
        rmCount++;

        size--;
        if (table[index] == r) {
            table[index] = r.next;
        } else {
            prev_r.next = r.next;
        }
        removeEntryFromLinkedList(r);
        return r.pv;
    }

    private void removeEntryFromLinkedList(Entry r) {
        Entry b = r.before;
        Entry a = r.after;
        b.after = a;
        a.before = b;
    }

    /**
     * Remove the first inserted value for Parameter p
     * 
     * @param p
     * @return the value removed or null if there was no value for p
     */
    public ParameterValue removeFirst(Parameter p) {
        int index = getHash(p) & (table.length - 1);
        Entry prev = table[index];
        if (prev == null) {
            return null;
        }

        Entry e = prev;
        Entry r = null;

        while (e != null) {
            if (e.pv.getParameter() == p) {
                r = e;
                break;
            }
            prev = e;
            e = e.next;
        }

        if (r == null) {
            return null;
        }
        rmCount++;
        size--;
        if (table[index] == r) {
            table[index] = r.next;
        } else {
            prev.next = r.next;
        }
        removeEntryFromLinkedList(r);

        return r.pv;
    }

    /**
     * this is copied from http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
     * 
     * 
     */
    static int roundUpToPowerOfTwo(int v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    /**
     * Creates an iterator which is positioned on the end of the list but returns all the elements added after the
     * iterator has been created.
     * <p>
     * Removing elements will invalidate the iterator.
     * 
     * @return
     */
    public Iterator<ParameterValue> tailIterator() {
        return new Iter(true);
    }

    @Override
    public Iterator<ParameterValue> iterator() {
        return new Iter(false);
    }

    /**
     * Adds all element to this collection
     * 
     * @param c
     * @return
     */
    @Override
    public boolean addAll(Collection<? extends ParameterValue> c) {
        int newSize = size + c.size();
        if (newSize > threshold) {
            int newCapacity = roundUpToPowerOfTwo(newSize);
            ensureCapacity(newCapacity);
            threshold = (int) (newCapacity * loadFactor);
        }

        for (ParameterValue pv : c) {
            doAdd(pv);
        }
        return false;
    }

    /**
     * Throws UnsupportedOperationException
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * Return true if the list contains the exact same ParameterValue. That means exactly the same object.
     * 
     * @param o
     * @return
     */
    @Override
    public boolean contains(Object o) {
        if (!(o instanceof ParameterValue)) {
            return false;
        }

        ParameterValue pv = (ParameterValue) o;

        int index = getHash(pv.getParameter()) & (table.length - 1);
        for (Entry e = table[index]; e != null; e = e.next) {
            if (e.pv == pv) {
                return true;
            }
        }
        return false;
    }

    /**
     * Throws UnsupportedOperationException
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Throws UnsupportedOperationException
     */
    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws UnsupportedOperationException
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws UnsupportedOperationException
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return size;
    }

    /**
     * Returns a copy of the list as array
     */
    @Override
    public Object[] toArray() {
        ParameterValue[] r = new ParameterValue[size];
        int i = 0;
        Iterator<ParameterValue> it = iterator();
        while (it.hasNext()) {
            r[i++] = it.next();
        }
        return r;
    }

    /**
     * Throws UnsupportedOperationException
     */
    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (ParameterValue pv : this) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(pv.toString());
        }
        sb.append("]");
        return sb.toString();
    }

    static class Entry {
        final ParameterValue pv;
        // next value for the same parameter
        Entry next;

        // ordering in the linked list
        Entry before, after;

        Entry(ParameterValue pv) {
            this.pv = pv;
        }
    }

    private final class Iter implements Iterator<ParameterValue> {
        Entry cur;
        int expectedRmCount;

        public Iter(boolean tail) {
            cur = tail ? head.before : head;
            expectedRmCount = rmCount;
        }

        @Override
        public boolean hasNext() {
            return cur.after != head;
        }

        @Override
        public ParameterValue next() {
            if (cur.after == head) {
                throw new NoSuchElementException();
            }
            if (rmCount != expectedRmCount) {
                throw new ConcurrentModificationException();
            }
            cur = cur.after;

            return cur.pv;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static ParameterValueList asList(ParameterValue... pvs) {
        ParameterValueList pvl = new ParameterValueList();
        for (ParameterValue pv : pvs) {
            pvl.add(pv);
        }
        return pvl;
    }

}
