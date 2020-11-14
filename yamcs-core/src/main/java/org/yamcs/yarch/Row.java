package org.yamcs.yarch;

import java.util.Arrays;

import org.yamcs.utils.IndexedList;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.TableColumnDefinition;

/**
 * 
 * This is like a tuple used in the context of table writing to collect values used for histograms and secondary
 * indices.
 * <p>
 * It is fixed size and unlike the normal tuples, it stores null values on the missing column places.
 * 
 * @author nm
 *
 */
public class Row {
    IndexedList<String, TableColumnDefinition> definition;
    Object[] values;
    byte[] key;

    public Row(IndexedList<String, TableColumnDefinition> definition) {
        this.definition = definition;
        this.values = new Object[definition.size()];
    }

    int getIndex(String colName) {
        return definition.getIndex(colName);
    }

    public Object get(int idx) {
        return values[idx];
    }

    public Object get(String colName) {
        int idx = getIndex(colName);
        return values[idx];
    }

    void set(int idx, Object value) {
        this.values[idx] = value;
    }

    void set(String colName, Object value) {
        int idx = getIndex(colName);
        this.values[idx] = value;
    }

    public void clear() {
        Arrays.fill(values, null);
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public byte[] getKey() {
        return key;
    }

    public TableColumnDefinition getColumnDefinition(String colName) {
        return definition.get(colName);
    }

    public String toString() {
        return StringConverter.arrayToHexString(key) + ":" + Arrays.toString(values);
    }
}
