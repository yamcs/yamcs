package org.yamcs.yarch.rocksdb;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Row;
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
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final RdbPartitionManager partitionManager;
    private final PartitioningSpec partitioningSpec;
    private final RdbTable table;

    static final byte[] zerobytes = new byte[0];
    Tablespace tablespace;
    volatile boolean closed = false;
    WriteOptions wopt;
    final HistogramWriter histoWriter;
    final SecondaryIndexWriter secondaryIndexWriter;
    TableDefinition tableDefinition;

    public RdbTableWriter(YarchDatabaseInstance ydb, RdbTable table, InsertMode mode) {
        super(ydb, table, mode);
        this.tableDefinition = table.getDefinition();
        this.partitioningSpec = tableDefinition.getPartitioningSpec();
        this.partitionManager = table.getPartitionManager();
        this.tablespace = table.getTablespace();
        this.table = table;

        wopt = new WriteOptions();
        if (mode == InsertMode.LOAD) {
            wopt.setSync(false);
            wopt.setDisableWAL(true);
        }
        histoWriter = table.getHistogramWriter();
        secondaryIndexWriter = table.getSecondaryIndexWriter();
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
            var cfh = rdb.createAndGetColumnFamilyHandle(table.cfName());

            switch (mode) {
            case INSERT:
                insert(rdb, cfh, partition, t);
                break;
            case UPSERT:
                upsert(rdb, cfh, partition, t);
                break;
            case INSERT_APPEND:
                insertAppend(rdb, cfh, partition, t);
                break;
            case UPSERT_APPEND:
                upsertAppend(rdb, cfh, partition, t);
                break;
            case LOAD:
                load(rdb, cfh, partition, t);
            }

            tablespace.dispose(rdb);
        } catch (IOException | RocksDBException | YarchException e) {
            log.error("failed to insert a record: ", e);
            YamcsServer.getServer().getCrashHandler(ydb.getYamcsInstance()).handleCrash("Archive",
                    "failed to insert a record in " + table.getName() + ": " + e);
        }
    }

    private boolean load(YRDB db, ColumnFamilyHandle cfh, RdbPartition partition, Tuple t)
            throws RocksDBException, YarchException {
        Row row = tableDefinition.generateRow(t);
        byte[] k = dbKey(partition.tbsIndex, row.getKey());
        byte[] v = tableDefinition.serializeValue(t, row);
        db.put(cfh, wopt, k, v);
        return true;
    }

    private void insert(YRDB rdb, ColumnFamilyHandle cfh, RdbPartition partition, Tuple t)
            throws RocksDBException, IOException {
        Row row = tableDefinition.generateRow(t);
        byte[] k = dbKey(partition.tbsIndex, row.getKey());

        if (rdb.get(cfh, k) != null) {
            return;
        }
        byte[] v = tableDefinition.serializeValue(t, row);

        writeToDb(rdb, cfh, partition, k, v, row);

        if (histoWriter != null) {
            histoWriter.addHistogram(row);
        }
    }

    private void upsert(YRDB rdb, ColumnFamilyHandle cfh, RdbPartition partition, Tuple t)
            throws RocksDBException, IOException {

        Row row = tableDefinition.generateRow(t);
        byte[] k = dbKey(partition.tbsIndex, row.getKey());
        byte[] v = tableDefinition.serializeValue(t, row);

        boolean updated = false;

        if (rdb.get(cfh, k) != null) {
            updated = true;
        }
        writeToDb(rdb, cfh, partition, k, v, row);

        if (histoWriter != null) {
            if (updated) {
                // TODO
            } else {
                histoWriter.addHistogram(row);
            }
        }
    }

    private void insertAppend(YRDB rdb, ColumnFamilyHandle cfh, RdbPartition partition, Tuple t)
            throws RocksDBException, IOException {
        Row row = tableDefinition.generateRow(t);
        byte[] dbKey = dbKey(partition.tbsIndex, row.getKey());

        boolean inserted = false;
        boolean updated = false;
        rdb.lock(dbKey);
        try {
            byte[] v = rdb.get(cfh, dbKey);
            if (v != null) {// append to an existing row
                Tuple oldt = tableDefinition.deserialize(dbKey, v);
                TupleDefinition tdef = t.getDefinition();
                TupleDefinition oldtdef = oldt.getDefinition();

                ArrayList<Object> cols = new ArrayList<Object>(oldt.getColumns().size() + t.getColumns().size());
                cols.addAll(oldt.getColumns());
                for (ColumnDefinition cd : tdef.getColumnDefinitions()) {
                    if (!oldtdef.hasColumn(cd.getName())) {
                        oldtdef.addColumn(cd);
                        cols.add(t.getColumn(cd.getName()));
                        updated = true;
                    }
                }
                if (updated) {
                    oldt.setColumns(cols);
                    v = tableDefinition.serializeValue(oldt, row);
                    writeToDb(rdb, cfh, partition, dbKey, v, row);
                }
            } else {// new row
                inserted = true;
                v = tableDefinition.serializeValue(t, row);
                writeToDb(rdb, cfh, partition, dbKey, v, row);
            }
        } finally {
            rdb.unlock(dbKey);
        }
        if (histoWriter != null) {
            if (inserted) {
                histoWriter.addHistogram(row);
            } // else TODO
        }
    }

    private void upsertAppend(YRDB rdb, ColumnFamilyHandle cfh, RdbPartition partition, Tuple t)
            throws RocksDBException, IOException {
        Row row = tableDefinition.generateRow(t);
        byte[] dbKey = dbKey(partition.tbsIndex, row.getKey());

        boolean inserted = false;
        boolean updated = false;

        rdb.lock(dbKey);
        try {
            byte[] v = rdb.get(cfh, dbKey);
            if (v != null) {// append to an existing row
                byte[] k = Arrays.copyOfRange(dbKey, TBS_INDEX_SIZE, dbKey.length);
                Tuple oldt = tableDefinition.deserialize(k, v);
                TupleDefinition tdef = t.getDefinition();
                TupleDefinition oldtdef = oldt.getDefinition();

                ArrayList<Object> cols = new ArrayList<>(oldt.getColumns().size() + t.getColumns().size());
                cols.addAll(oldt.getColumns());
                for (ColumnDefinition cd : tdef.getColumnDefinitions()) {
                    if (oldtdef.hasColumn(cd.getName())) {
                        // currently always says it changed. Not sure if it's worth
                        // checking if different
                        cols.set(oldt.getColumnIndex(cd.getName()), t.getColumn(cd.getName()));
                        updated = true;
                    } else {
                        oldtdef.addColumn(cd);
                        cols.add(t.getColumn(cd.getName()));
                        updated = true;
                    }
                }
                if (updated) {
                    oldt.setColumns(cols);
                    v = tableDefinition.serializeValue(oldt, row);
                    writeToDb(rdb, cfh, partition, dbKey, v, row);
                }
            } else {// new row
                inserted = true;
                v = tableDefinition.serializeValue(t, row);
                writeToDb(rdb, cfh, partition, dbKey, v, row);
            }
        } finally {
            rdb.unlock(dbKey);
        }

        if (histoWriter != null) {
            if (inserted) {
                histoWriter.addHistogram(row);
            } // else TODO
        }
    }

    private void writeToDb(YRDB rdb, ColumnFamilyHandle cfh, RdbPartition partition, byte[] key, byte[] value, Row row)
            throws RocksDBException {

        if (secondaryIndexWriter == null) {
            rdb.put(cfh, key, value);
            return;
        }
        if (rdb == tablespace.getRdb()) {
            try (WriteBatch writeBatch = new WriteBatch();
                    WriteOptions writeOpts = new WriteOptions()) {
                writeBatch.put(cfh, key, value);
                secondaryIndexWriter.addTuple(writeBatch, row, partition);
                rdb.write(writeOpts, writeBatch);
            }
        } else {// secondary index and main data go into different databases, we cannot perform the write in a batch
            rdb.put(cfh, key, value);
            cfh = tablespace.getRdb().getColumnFamilyHandle(table.cfName());
            try (WriteBatch writeBatch = new WriteBatch();
                    WriteOptions writeOpts = new WriteOptions()) {
                writeBatch.put(cfh, key, value);
                secondaryIndexWriter.addTuple(writeBatch, row, partition);
                tablespace.getRdb().write(writeOpts, writeBatch);
            }
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
