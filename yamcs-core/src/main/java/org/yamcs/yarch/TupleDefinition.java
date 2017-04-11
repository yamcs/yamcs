package org.yamcs.yarch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TupleDefinition implements Serializable, Cloneable {
    private static final long serialVersionUID = 200805301445L;
    private ArrayList<ColumnDefinition> columnDefinitions = new ArrayList<ColumnDefinition>();
    private HashMap<String,Integer> columnNameIndex = new HashMap<String,Integer>();
    public static final int MAX_COLS = 32000;
    
    public List<ColumnDefinition> getColumnDefinitions() {
        return columnDefinitions;
    }

    public void addColumn(String name, DataType type) {
        ColumnDefinition c = new ColumnDefinition(name,type);
        addColumn(c);
    }

    public void addColumn(ColumnDefinition c) {
        if(columnNameIndex.containsKey(c.getName())) {
            throw new IllegalArgumentException("Tuple has already a column '"+c.getName()+"'");
        }
        columnDefinitions.add(c);
        columnNameIndex.put(c.getName(), columnDefinitions.size()-1);
    }

    /**
     * returns the index of the column with name or -1 if there is no such column
     * 
     * @param name
     * @return the index of the column with name or -1 if there is no such column 
     */
    public int getColumnIndex(String name) {
        Integer i = columnNameIndex.get(name);
        if(i==null) {
            return -1;
        } else {
            return i;
        }
    }
    
    public boolean hasColumn(String name) {
        return columnNameIndex.containsKey(name);
    }
    /**
     * Get a column definition by name
     * 
     * @param name
     * @return the column definition of the named column or null if the table does not have a column by that name 
     */
    public ColumnDefinition getColumn(String name) {
        Integer i = columnNameIndex.get(name);
        if(i==null) {
            return null;
        } else {
            return columnDefinitions.get(i);
        }
    }
  
    public ColumnDefinition getColumn(int index) {
        return columnDefinitions.get(index);
    }

    /**
     * renames the column - this should not be used when the tuple is in used as there is no synchronization around it.
     *  
     * @param oldName
     * @param newName
     */
    void renameColumn(String oldName, String newName) {
        int idx = columnNameIndex.remove(oldName);
        ColumnDefinition oldCd = columnDefinitions.get(idx);
        
        ColumnDefinition newCd = new ColumnDefinition(newName, oldCd.type);
        columnDefinitions.set(idx, newCd);
        columnNameIndex.put(newName, idx);
    }
    
    
    /**
     * Reads a tuple from an IO inputStream(socket).
     * @param inputStream
     * @return
     * @throws IOException
     
    public Tuple read(java.io.DataInput inputStream) throws IOException {
        Object[] columns=new Object[getColumnDefinitions().size()];
        int i=0;
        for(ColumnDefinition cd:getColumnDefinitions()) {
            columns[i]=cd.deserialize(inputStream);
            if(columns[i]==null) {
                return null;
            }
            i++;
        }
        return new Tuple(this,columns);
    }

    public void write(java.io.DataOutputStream outputStream, Tuple t) throws IOException {
        for(ColumnDefinition cd:columnDefinitions) {
            cd.serialize(outputStream,t.getColumn(cd.getName()));
        }
    }
*/

    
    /**
     * Returns a string "(col1 type, col2 type2, ....)"
     * suitable to be used in create stream
     * 
     */
    public String getStringDefinition() {
        StringBuilder sb=new StringBuilder();
        sb.append("(");
        boolean first=true;
        for(ColumnDefinition cd:getColumnDefinitions()) {
            if(!first) {
                sb.append(", ");
            }
            else first=false;
            sb.append(cd.getStringDefinition());
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns a string "col1 type, col2 type2, ...."
     * (without parenthesis) suitable to be used in create table
     * 
     */
    public String getStringDefinition1() {
        StringBuilder sb = new StringBuilder();
        boolean first=true;
        for(ColumnDefinition cd:getColumnDefinitions()) {
            if(!first) {
                sb.append(", ");
            }
            else first=false;
            sb.append(cd.toString());
        }
        return sb.toString();
    }


    /**
     * 
     * @return number of columns part of the tuple
     */
    public int size() {
        return columnDefinitions.size();
    }
    
    /**
     * 
     * @return a copy of the tuple definition that can be used to add columns
     */
    public TupleDefinition copy() {
        TupleDefinition ntd=new TupleDefinition();
        ntd.columnDefinitions.addAll(columnDefinitions);
        ntd.columnNameIndex.putAll(columnNameIndex);
        return ntd;
    }
    @Override
    public String toString() {
        return getStringDefinition();
    }

 

    
}