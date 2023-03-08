package org.yamcs.yarch;

import java.util.HashMap;
import java.util.Map;

import org.rocksdb.Snapshot;
import org.yamcs.logging.Log;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;

/**
 * Keeps track of attributes associated with an execution context of a query: tablespace, database and snapshots
 * <p>
 * The context has to be closed to release the snapshots (otherwise
 *
 */
public class ExecutionContext implements AutoCloseable {
    final YarchDatabaseInstance db;
    Tablespace tablespace;
    Map<YRDB, Snapshot> snapshots;
    private volatile boolean closed = false;
    final static Log log = new Log(ExecutionContext.class);
    // used to keep a stack trace when created. to remove
    Exception e;

    public ExecutionContext(YarchDatabaseInstance db) {
        this.db = db;
        e = new Exception();
    }

    public YarchDatabaseInstance getDb() {
        return db;
    }

    public void setTablespace(Tablespace tablespace) {
        if (this.tablespace != null && this.tablespace != tablespace) {
            throw new IllegalStateException("Multiple tablespaces not supported");
        }
        this.tablespace = tablespace;
    }

    public Tablespace getTablespace() {
        return tablespace;
    }

    public synchronized Snapshot getSnapshot(YRDB rdb) {
        if (closed) {
            throw new IllegalStateException("ExecutionContext is closed");
        }
        if (snapshots == null) {
            snapshots = new HashMap<>();
        }
        return snapshots.computeIfAbsent(rdb, x -> rdb.getSnapshot());
    }

    public synchronized void close() {
        closed = true;
        if(snapshots != null) {
            for (Map.Entry<YRDB, Snapshot> me : snapshots.entrySet()) {
                me.getKey().releaseSnapshot(me.getValue());
                me.getValue().close();
            }
        }
        snapshots = null;
    }

    public synchronized void addSnapshot(YRDB rdb, Snapshot snapshot) {
        if (closed) {
            throw new IllegalStateException("ExecutionContext is closed");
        }

        if (snapshots == null) {
            snapshots = new HashMap<>();
        }
        if (snapshots.containsKey(rdb)) {
            throw new IllegalStateException("Already have a snapshot for this database");
        }
        snapshots.put(rdb, snapshot);
    }

    @Override
    public void finalize() {
        if (!closed && snapshots != null) {
            log.error("ExecutionContext " + this + " not closed", e);
        }
    }
}
