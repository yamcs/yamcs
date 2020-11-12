package org.yamcs.yarch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.yamcs.LimitExceededException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * Stores properties for table columns
 * 
 * @author nm
 *
 */
public class TableColumnDefinition extends ColumnDefinition {
    ColumnSerializer<Object> serializer;
    BiMap<String, Short> enumValues;
    boolean autoincrement;

    //used to get the next value for this column when autoincrement = true
    Sequence sequence;
    
    public TableColumnDefinition(String name, DataType type) {
        super(name, type);
        if (type == DataType.ENUM) {
            enumValues = HashBiMap.create();
        }
    }

    public TableColumnDefinition(ColumnDefinition c) {
        this(c.name, c.type);
    }

    /**
     * Copies the definition into a new one with a different type. Used for some table migrations operations between
     * versions.
     */
    TableColumnDefinition(TableColumnDefinition tcd, DataType dataType) {
        super(tcd.name, dataType);
        this.serializer = tcd.serializer;
        this.enumValues = tcd.enumValues;
        this.autoincrement = tcd.autoincrement;
    }

    /**
     * Copy constructor
     */
    public TableColumnDefinition(TableColumnDefinition tcd) {
        this(tcd, tcd.type);
    }

    public void setAutoIncrement(boolean b) {
        this.autoincrement = b;
    }

    public <T extends Object> void serialize(DataOutputStream dos, T v) throws IOException {
        serializer.serialize(dos, v);
    }

    public Object deserialize(DataInputStream dis) throws IOException {
        return serializer.deserialize(dis, this);
    }

    public void setEnumValues(BiMap<String, Short> enumValues) {
        this.enumValues = enumValues;
    }

    public <T extends Object> ColumnSerializer<T> getSerializer() {
        return (ColumnSerializer<T>) serializer;
    }

    public Sequence getSequence() {
        return sequence;
    }
    
    public Short getEnumIndex(String value) {
        if (enumValues == null) {
            throw new IllegalArgumentException("column named '" + name + "' is not an enum");
        }
        return enumValues.get(value);
    }

    public String getEnumValue(short idx) {
        if (enumValues == null) {
            throw new IllegalArgumentException("column named '" + name + "' is not an enum");
        }
        return enumValues.inverse().get(idx);
    }

    short addEnumValue(String value) {
        if (enumValues.containsKey(value)) {
            throw new IllegalArgumentException("There is already a value '" + value + "'");
        }
        if (enumValues.size() >= (int) Short.MAX_VALUE) {
            throw new LimitExceededException(
                    "Number of enum values for column " + name + " is exceeding the limit " + Short.MAX_VALUE);
        }

        short x = (short) enumValues.size();
        enumValues.put(value, x);

        return x;
    }

    public BiMap<String, Short> getEnumValues() {
        return enumValues;
    }
    
    public boolean isAutoIncrement() {
        return autoincrement;
    }
    
    public void setSequence(Sequence sequence) {
        this.sequence = sequence;
    }
    
    @Override
    public String toString() {
        return String.format("%s %s %s",name, type, autoincrement?"auto_increment":"");
    }

   
}
