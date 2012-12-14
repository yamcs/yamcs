package org.yamcs.yarch;

import org.yamcs.yarch.DataType._type;


public class ColumnDefinition {

    protected final String name;
    protected final DataType type;
    
    public ColumnDefinition(String name, DataType type) {
        super();
        this.name=name;
        this.type=type;
    }

    public String javaType() {
        return type.javaType();
    }

    public DataType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns a sql like "name type" definition for the column 
     * @return
     */
    public Object getStringDefinition() {
        if(type.val==_type.PROTOBUF) {
            return String.format("%s PROTOBUF('%s')", name, type.getClassName());
        } else {
            return String.format("%s %s",name, type);
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s %s",name, type);
    }

}
