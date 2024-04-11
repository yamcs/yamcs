package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Contains the tuple value (as an array of Columns) together with a pointer to its definition
 * 
 */
public class Tuple {
    private TupleDefinition definition;
    List<Object> columns;

    /**
     * Create a new tuple with no column.
     * <p>
     * Can be used by the {@link #addColumn(String, DataType, Object)} methods
     *
     */
    public Tuple() {
        this.definition = new TupleDefinition();
        this.columns = new ArrayList<>();
    }

    public Tuple(TupleDefinition definition, List<Object> columns) {
        if (definition.size() != columns.size()) {
            throw new IllegalArgumentException("columns size does not match the definition size");
        }
        this.setDefinition(definition);
        this.columns = columns;
    }

    public Tuple(TupleDefinition definition, Object[] columns) {
        this(definition, new ArrayList<>(Arrays.asList(columns)));
    }

    /**
     * Create a tuple with all the column values set to null.
     * 
     * @param tdef
     */
    public Tuple(TupleDefinition tdef) {
        columns = new ArrayList<>(Collections.nCopies(tdef.size(), null));
    }

    public void setDefinition(TupleDefinition definition) {
        this.definition = definition;
    }

    public TupleDefinition getDefinition() {
        return definition;
    }

    public List<?> getColumns() {
        return columns;
    }

    public void setColumns(List<Object> cols) {
        this.columns = cols;
    }

    public void setColumn(int index, Object value) {
        columns.set(index, value);
    }

    public void setColumn(String colName, Object value) {
        columns.set(getColumnIndex(colName), value);
    }

    /**
     * returns the index of the column with name or -1 if there is no such column
     * 
     * @param colName
     *            - the name of the column
     * @return the index of the column with name or -1 if there is no such column
     */
    public int getColumnIndex(String colName) {
        return definition.getColumnIndex(colName);
    }

    @SuppressWarnings("unchecked")
    public <T> T getColumn(String colName) {
        int i = definition.getColumnIndex(colName);
        if (i == -1) {
            return null;
        }
        return (T) columns.get(i);
    }

    /**
     * Get the value of column as long.
     * <p>
     * Throws exception if the column does not exist or is of different type
     * 
     * @param colName
     * @return
     */
    public long getLongColumn(String colName) {
        return getColumn(colName);
    }

    public long getTimestampColumn(String colName) {
        return getColumn(colName);
    }

    /**
     * Get the value of column as boolean.
     * <p>
     * Throws exception if the column does not exist or is of different type
     * 
     * @param colName
     * @return
     */
    public boolean getBooleanColumn(String colName) {
        return getColumn(colName);
    }

    /**
     * Get the value of column as byte.
     * <p>
     * Throws exception if the column does not exist or is of different type
     * 
     * @param colName
     * @return
     */
    public byte getByteColumn(String colName) {
        return getColumn(colName);
    }

    /**
     * Get the value of column as short.
     * <p>
     * Throws exception if the column does not exist or is of different type
     * 
     * @param colName
     * @return
     */
    public short getShortColumn(String colName) {
        return getColumn(colName);
    }

    /**
     * Get the value of column as int.
     * <p>
     * Throws exception if the column does not exist or is of different type
     * 
     * @param colName
     * @return
     */
    public int getIntColumn(String colName) {
        return getColumn(colName);
    }

    /**
     * Get the value of column as double.
     * <p>
     * Throws exception if the column does not exist or is of different type
     * 
     * @param colName
     * @return
     */
    public double getDoubleColumn(String colName) {
        return getColumn(colName);
    }

    public ColumnDefinition getColumnDefinition(String colName) {
        int i = definition.getColumnIndex(colName);
        if (i == -1) {
            throw new IllegalArgumentException("invalid column " + colName);
        }
        return definition.getColumn(i);
    }

    public ColumnDefinition getColumnDefinition(int i) {
        return definition.getColumn(i);
    }

    public boolean hasColumn(String colName) {
        return (definition.getColumnIndex(colName) != -1);
    }

    public Object getColumn(int i) {
        return columns.get(i);
    }

    /**
     * Add a TIMESTAMP column
     * 
     * @param colName
     * @param colValue
     */
    public void addTimestampColumn(String colName, long colValue) {
        addColumn(colName, DataType.TIMESTAMP, colValue);
    }

    /**
     * Add a INT column
     * 
     * @param colName
     * @param colValue
     */
    public void addColumn(String colName, int colValue) {
        addColumn(colName, DataType.INT, colValue);
    }

    /**
     * Add a BOOLEAN column
     * 
     * @param colName
     * @param colValue
     */
    public void addColumn(String colName, boolean colValue) {
        addColumn(colName, DataType.BOOLEAN, colValue);
    }

    /**
     * Add a LONG column
     * 
     * @param colName
     * @param colValue
     */
    public void addColumn(String colName, long colValue) {
        addColumn(colName, DataType.LONG, colValue);
    }

    /**
     * Add a STRING column
     * 
     * @param colName
     * @param colValue
     */
    public void addColumn(String colName, String colValue) {
        addColumn(colName, DataType.STRING, colValue);
    }

    /**
     * Add an ENUM column
     * 
     * @param colName
     * @param colValue
     */
    public void addEnumColumn(String colName, String colValue) {
        addColumn(colName, DataType.ENUM, colValue);
    }

    public void addColumn(String colName, DataType type, Object colValue) {
        definition.addColumn(colName, type);
        columns.add(colValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T removeColumn(String colName) {
        int idx = definition.removeColumn(colName);
        if (idx != -1) {
            return (T) columns.remove(idx);
        } else {
            return null;
        }
    }

    /**
     * 
     * @return return the number of columns
     */
    public int size() {
        return columns.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("(");
        for (Object c : columns) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append(String.valueOf(c));
        }
        sb.append(")");
        return sb.toString();
    }

}
