package org.yamcs.yarch;

public abstract class TableWriter implements StreamSubscriber {         
    public enum InsertMode {
        /**
         * insert rows whose key do not exist, ignore the others
         */
        INSERT,
        /**
         * insert rows as they come, overwriting old values if the key already exist
         */
        UPSERT,
        /**
         * like INSERT but if the row already exist, append to it all the columns that are not already there
         */
        INSERT_APPEND,
        /**
         * like INSERT_APPEND but if the row already exists, add all the columns from the new row, overwriting old values if necessary
         */
        UPSERT_APPEND,
        /**
         * used for bulk load when we know that the data cannot be in the table
         */
        LOAD,
    }
    
    final protected TableDefinition tableDefinition;
    final protected InsertMode mode;
    final protected YarchDatabaseInstance ydb;
    
    public TableWriter(YarchDatabaseInstance ydb, TableDefinition tableDefinition, InsertMode mode) {
        this.tableDefinition = tableDefinition;
        this.mode = mode;
        this.ydb = ydb;
    }
    
    public TableDefinition getTableDefinition() {
        return tableDefinition;
    }

    /**
     * close histogram db and any open resources
     */
    public abstract void close();
}