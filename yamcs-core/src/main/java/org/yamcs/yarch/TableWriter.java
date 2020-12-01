package org.yamcs.yarch;

import java.util.concurrent.CompletableFuture;

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
         * like INSERT but do not update histograms.
         * <p>
         * used for bulk load when we know that the data cannot be in the table
         */
        LOAD,
    }
    
    final protected Table table;
    final protected InsertMode mode;
    final protected YarchDatabaseInstance ydb;
    final private CompletableFuture<Void> closeFuture = new CompletableFuture<Void>();
    
    public TableWriter(YarchDatabaseInstance ydb, Table table, InsertMode mode) {
        this.table = table;
        this.mode = mode;
        this.ydb = ydb;
    }
    
    /**
     * future which will be called (completed) when the writer is closed.
     * 
     * @return
     */
    public CompletableFuture<Void> closeFuture() {
        return closeFuture;
    }

    /**
     * close writer and any open resources
     * <p> call the close future after closing has been completed
     */
    public void close() {
        doClose();
        closeFuture.complete(null);
    }
    
    
    protected abstract void doClose();
}