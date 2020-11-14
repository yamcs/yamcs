package org.yamcs.yarch;


public abstract class Table {
    final protected TableDefinition tableDefinition;
    
    public Table(TableDefinition tblDef) {
        this.tableDefinition = tblDef;
    }
    

    public TableDefinition getDefinition() {
        return tableDefinition;
    }


    public String getName() {
        return tableDefinition.getName();
    }
    
}
