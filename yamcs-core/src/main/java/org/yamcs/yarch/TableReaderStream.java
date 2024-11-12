package org.yamcs.yarch;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements skeleton for table streamer that uses PartitionManager to handle partitioning.
 * 
 */
public class TableReaderStream extends Stream implements Runnable, TableVisitor {
    static AtomicInteger count = new AtomicInteger(0);
    TableWalker tblIterator;

    protected TableDefinition tableDefinition;;
    Thread thread;

    public TableReaderStream(YarchDatabaseInstance ydb, TableDefinition tblDef, TableWalker iterator) {
        super(ydb, tblDef.getName() + "_" + count.getAndIncrement(),
                tblDef.getTupleDefinition());
        this.tblIterator = iterator;
        this.tableDefinition = tblDef;
    }

    @Override
    public void doStart() {
        thread = new Thread(this, "RdbTableReaderStream[" + getName() + "]");
        thread.start();
    }

    @Override
    public void run() {
        log.debug("starting a table stream from table {} ", tableDefinition.getName());
        try {
            tblIterator.walk(this);
        } catch (Exception e) {
            log.error("got exception ", e);
        } finally {
            close();
        }
    }

    @Override
    public Action visit(byte[] key, byte[] value) {
        emitTuple(dataToTuple(key, value));
        return ACTION_CONTINUE;
    }

    protected Tuple dataToTuple(byte[] k, byte[] v) {
        return tableDefinition.deserialize(k, v);
    }

    @Override
    public void doClose() {
        try {
            tblIterator.close();
        } catch (YarchException e) {
            log.error("got exception ", e);
        }
        // when the thread is blocked on sending something (for example to a merge stream), if we don't call this, it
        // will be hanging forever
        if (Thread.currentThread() != thread) {
            thread.interrupt();
        }
    }

    public TableDefinition getTableDefinition() {
        return tableDefinition;
    }

    // this is a lexicographic comparison which returns 0 if one of the array is
    // a subarray of the other one
    // it is useful when the filter key is shorter than the index key
    protected int compare(byte[] a1, byte[] a2) {
        for (int i = 0; i < a1.length && i < a2.length; i++) {
            int d = (a1[i] & 0xFF) - (a2[i] & 0xFF);
            if (d != 0) {
                return d;
            }
        }
        return 0;
    }
}
