package org.yamcs.yarch;

import java.util.Arrays;
import java.util.List;

/**
 * Contains the tuple value (as an array of Columns) together with a pointer to its definition
 * 
 * @author nm
 *
 */
public class Tuple {
    private TupleDefinition definition;
    List<Object> columns;

    public Tuple(TupleDefinition definition, List<Object> columns) {
        if (definition.size() != columns.size()) {
            throw new IllegalArgumentException("columns size does not match the definition size");
        }
        this.setDefinition(definition);
        this.columns = columns;
    }

    public Tuple(TupleDefinition definition, Object[] columns) {
        this(definition, Arrays.asList(columns));
    }

    public void setDefinition(TupleDefinition definition) {
        this.definition = definition;
    }

    public TupleDefinition getDefinition() {
        return definition;
    }

    public List<Object> getColumns() {
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

    public int getColumnIndex(String colName) {
        return definition.getColumnIndex(colName);
    }

    public Object getColumn(String colName) {
        int i = definition.getColumnIndex(colName);
        if (i == -1) {
            return null;
        }
        return columns.get(i);
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

    public void addColumn(String colName, DataType type, Object colValue) {
        definition.addColumn(colName, type);
        columns.add(colValue);
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
            sb.append(c.toString());
        }
        sb.append(")");
        return sb.toString();
    }

}
