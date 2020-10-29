package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Keeps track of attributes associated with an execution context
 * 
 * @author nm
 *
 */
public class ExecutionContext {
    final YarchDatabaseInstance db;

    public ExecutionContext(YarchDatabaseInstance db) {
        this.db = db;
    }

    public YarchDatabaseInstance getDb() {
        return db;
    }
}
