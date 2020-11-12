package org.yamcs.yarch.rocksdb;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;;

/**
 * table writer for the rocksdb2 engine.
 * <p>
 * See {@link Tablespace} for a description of the data format.
 * 
 * <p>
 * There might be multiple objects of this class writing in the same table. We perform locking at record level using the
 * {@link YRDB#lock(byte[])} function.
 * 
 * <p>
 * The histograms are written by the {@link HistogramWriter}.
 *
 * 
 * @author nm
 *
 */
public class RdbTableWriter extends TableWriter {
    private final RdbPartitionManager partitionManager;
    private final PartitioningSpec partitioningSpec;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    static final byte[] zerobytes = new byte[0];
    Tablespace tablespace;
    volatile boolean closed = false;
    WriteOptions wopt;
    final HistogramWriter histoWriter;

    public RdbTableWriter(Tablespace tablespace, YarchDatabaseInstance ydb, TableDefinition tableDefinition,
            InsertMode mode) {
        super(ydb, tableDefinition, mode);
        this.partitioningSpec = tableDefinition.getPartitioningSpec();
        this.partitionManager = tablespace.getPartitionManager(tableDefinition);
        this.tablespace = tablespace;

        wopt = new WriteOptions();
        if (mode == InsertMode.LOAD) {
            wopt.setSync(false);
            wopt.setDisableWAL(true);
        }
        histoWriter = tablespace.getHistogramWriter(tableDefinition);
    }

    @Override
    public void onTuple(Stream stream, Tuple t) {
        /*
         * since this method is not synchronised to the RdbStorageEngine#dropTable, it could be that one records is
         * still written after the table has been dropped. This happens if the onTuple is already running when dropTable
         * is called and the write operation ends up after the deleteRange used in the dropTable.
         * 
         * The record will have an invalid tbsIndex (not part of the tablespace metadata) so we can safely ignore it.
         * 
         * The closed volatile check below is mostly for safety if someone keeps pushing data after the table has been
         * dropped.
         */

        if (closed) {
            return;
        }
        try {
            RdbPartition partition = getDbPartition(t);
            YRDB rdb = tablespace.getRdb(partition.dir, false);

            boolean inserted = false;
            boolean updated = false;
            switch (mode) {
            case INSERT:
                inserted = insert(rdb, partition, t);
                break;
            case UPSERT:
                inserted = upsert(rdb, partition, t);
                updated = !inserted;
                break;
            case INSERT_APPEND:
                inserted = insertAppend(rdb, partition, t);
                break;
            case UPSERT_APPEND:
                inserted = upsertAppend(rdb, partition, t);
                updated = !inserted;
                break;
            case LOAD:
                load(rdb, partition, t);
            }

            if (inserted && histoWriter != null && mode != InsertMode.LOAD) {
                histoWriter.addHistogram(t);
            }
            if (updated && histoWriter != null) {
                // TODO updateHistogram(t);
            }
            tablespace.dispose(rdb);
        } catch (IOException | RocksDBException | YarchException e) {
            log.error("failed to insert a record: ", e);
            YamcsServer.getServer().getCrashHandler(ydb.getYamcsInstance()).handleCrash("Archive",
                    "failed to insert a record in " + tableDefinition.getName() + ": " + e);
        }

    }

    private boolean load(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException, YarchException {
        byte[] k = dbKey(partition.tbsIndex, tableDefinition.serializeKey(t));
        byte[] v = tableDefinition.serializeValue(t);
        db.put(wopt, k, v);
        return true;
    }

    private boolean insert(YRDB rdb, RdbPartition partition, Tuple t) throws RocksDBException, YarchException {
        byte[] k = dbKey(partition.tbsIndex, tableDefinition.serializeKey(t));
        byte[] v = tableDefinition.serializeValue(t);

        if (rdb.get(k) == null) {
            rdb.put(k, v);
            return true;
        } else {
            return false;
        }
    }

    private boolean upsert(YRDB rdb, RdbPartition partition, Tuple t) throws RocksDBException, YarchException {
        byte[] k = dbKey(partition.tbsIndex, tableDefinition.serializeKey(t));
        byte[] v = tableDefinition.serializeValue(t);

        if (rdb.get(k) == null) {
            rdb.put(k, v);
            return true;
        } else {
            rdb.put(k, v);
            return false;
        }
    }

    /**
     * returns true if a new record has been inserted and false if an record was
     * already existing with this key (even if modified)
     * 
     * @param partition
     * @throws RocksDBException
     * @throws YarchException 
     */
    private boolean insertAppend(YRDB rdb, RdbPartition partition, Tuple t) throws RocksDBException, YarchException {
        byte[] dbKey = dbKey(partition.tbsIndex, tableDefinition.serializeKey(t));
        rdb.lock(dbKey);
        try {
            byte[] v = rdb.get(dbKey);
            boolean inserted = false;
            if (v != null) {// append to an existing row
                Tuple oldt = tableDefinition.deserialize(dbKey, v);
                TupleDefinition tdef = t.getDefinition();
                TupleDefinition oldtdef = oldt.getDefinition();

                boolean changed = false;
                ArrayList<Object> cols = new ArrayList<Object>(oldt.getColumns().size() + t.getColumns().size());
                cols.addAll(oldt.getColumns());
                for (ColumnDefinition cd : tdef.getColumnDefinitions()) {
                    if (!oldtdef.hasColumn(cd.getName())) {
                        oldtdef.addColumn(cd);
                        cols.add(t.getColumn(cd.getName()));
                        changed = true;
                    }
                }
                if (changed) {
                    oldt.setColumns(cols);
                    v = tableDefinition.serializeValue(oldt);
                    rdb.put(dbKey, v);
                }
            } else {// new row
                inserted = true;
                v = tableDefinition.serializeValue(t);
                rdb.put(dbKey, v);
            }
            return inserted;
        } finally {
            rdb.unlock(dbKey);
        }
    }

    private boolean upsertAppend(YRDB rdb, RdbPartition partition, Tuple t) throws RocksDBException, YarchException {
        byte[] dbKey = dbKey(partition.tbsIndex, tableDefinition.serializeKey(t));
        rdb.lock(dbKey);
        try {
            byte[] v = rdb.get(dbKey);
            boolean inserted = false;
            if (v != null) {// append to an existing row
                byte[] k = Arrays.copyOfRange(dbKey, TBS_INDEX_SIZE, dbKey.length);
                Tuple oldt = tableDefinition.deserialize(k, v);
                TupleDefinition tdef = t.getDefinition();
                TupleDefinition oldtdef = oldt.getDefinition();

                boolean changed = false;
                ArrayList<Object> cols = new ArrayList<>(oldt.getColumns().size() + t.getColumns().size());
                cols.addAll(oldt.getColumns());
                for (ColumnDefinition cd : tdef.getColumnDefinitions()) {
                    if (oldtdef.hasColumn(cd.getName())) {
                        // currently always says it changed. Not sure if it's worth
                        // checking if different
                        cols.set(oldt.getColumnIndex(cd.getName()), t.getColumn(cd.getName()));
                        changed = true;
                    } else {
                        oldtdef.addColumn(cd);
                        cols.add(t.getColumn(cd.getName()));
                        changed = true;
                    }
                }
                if (changed) {
                    oldt.setColumns(cols);
                    v = tableDefinition.serializeValue(oldt);
                    rdb.put(dbKey, v);
                }
            } else {// new row
                inserted = true;
                v = tableDefinition.serializeValue(t);
                rdb.put(dbKey, v);
            }
            return inserted;
        } finally {
            rdb.unlock(dbKey);
        }
    }

    /**
     * get the filename where the tuple would fit (can be a partition)
     * 
     * @param t
     * @return the partition where the tuple fits
     * @throws IOException
     *             if there was an error while creating the directories where
     *             the file should be located
     */
    public RdbPartition getDbPartition(Tuple t) throws IOException {
        long time = TimeEncoding.INVALID_INSTANT;
        Object value = null;
        if (partitioningSpec.timeColumn != null) {
            time = (Long) t.getColumn(partitioningSpec.timeColumn);
        }
        if (partitioningSpec.valueColumn != null) {
            value = t.getColumn(partitioningSpec.valueColumn);
            ColumnDefinition cd = tableDefinition.getColumnDefinition(partitioningSpec.valueColumn);
            if (cd.getType() == DataType.ENUM) {
                value = tableDefinition.addAndGetEnumValue(partitioningSpec.valueColumn, (String) value);
            }
        }
        return (RdbPartition) partitionManager.createAndGetPartition(time, value);
    }

    protected void doClose() {
        if (closed) {
            return;
        }
        closed = true;
    }

    @Override
    public void streamClosed(Stream stream) {
        log.debug("Stream {} closed", stream.getName());
        close();
    }
}
