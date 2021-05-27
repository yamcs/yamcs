package org.yamcs.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * List which is indexed by a key in addition to its natural integer index
 * <p>
 * Does not allow removal
 * 
 * @author nm
 *
 */
public class IndexedList<K, V> implements Iterable<V> {
    protected ArrayList<V> values = new ArrayList<>();
    protected HashMap<K, Integer> keys = new HashMap<>();

    public IndexedList() {
        values = new ArrayList<>();
        keys = new HashMap<>();
    }

    public IndexedList(IndexedList<K, V> list) {
        values = new ArrayList<>(list.values);
        keys = new HashMap<>(list.keys);
    }

    public IndexedList(int size) {
        values = new ArrayList<>(size);
        keys = new HashMap<>(size * 2);
    }

    public void add(K key, V value) {
        if (keys.containsKey(key)) {
            throw new IllegalArgumentException("An entry '" + key + "' already exists");
        }
        values.add(value);
        keys.put(key, values.size() - 1);
    }

    /**
     * returns the index of the element with key or -1 if there is no such element
     * 
     * @param key
     * @return returns the index of the element with key or -1 if there is no such element
     */
    public int getIndex(K key) {
        Integer idx = keys.get(key);
        if (idx == null) {
            return -1;
        } else {
            return idx;
        }
    }

    /**
     * 
     * @param key
     * @return true if the list contains the key
     */
    public boolean hasKey(K key) {
        return keys.containsKey(key);
    }

    /**
     * Returns the value mapped to the key or null if there is no such element
     * 
     * @param key
     * @return
     */
    public V get(K key) {
        Integer idx = keys.get(key);
        if (idx == null) {
            return null;
        }
        return values.get(idx);
    }

    /**
     * 
     * @see List#get(int)
     * @param idx
     * @return
     */
    public V get(int idx) {
        return values.get(idx);
    }

    @Override
    public Iterator<V> iterator() {
        return values.iterator();
    }

    /**
     * 
     * @return returns the size of the list.
     */
    public int size() {
        return values.size();
    }

    public void changeKey(K oldKey, K newKey) {
        Integer x = keys.remove(oldKey);
        if (x==null) {
            throw new IllegalArgumentException("key inexistent");
        }
        keys.put(newKey, x);
    }

    public List<V> getList() {
        return Collections.unmodifiableList(values);
    }

    public void set(int idx, V value) {
        values.set(idx, value);
    }

    public String toString() {
        return values.toString();
    }
}
