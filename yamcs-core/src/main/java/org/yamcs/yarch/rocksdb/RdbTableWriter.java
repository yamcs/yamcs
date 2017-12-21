package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;;

/**
 * table writer that prepends the partition binary value in front of the key
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

    public RdbTableWriter(Tablespace tablespace, YarchDatabaseInstance ydb, TableDefinition tableDefinition,
            InsertMode mode, RdbPartitionManager pm) {
        super(ydb, tableDefinition, mode);
        this.partitioningSpec = tableDefinition.getPartitioningSpec();
        this.partitionManager = pm;
        this.tablespace = tablespace;
    }

    @Override
    public void onTuple(Stream stream, Tuple t) {
        try {
            RdbPartition partition = getDbPartition(t);
            YRDB db = tablespace.getRdb(partition.dir, false);

            boolean inserted = false;
            boolean updated = false;
            switch (mode) {
            case INSERT:
                inserted = insert(db, partition, t);
                break;
            case UPSERT:
                inserted = upsert(db, partition, t);
                updated = !inserted;
                break;
            case INSERT_APPEND:
                inserted = insertAppend(db, partition, t);
                break;
            case UPSERT_APPEND:
                inserted = upsertAppend(db, partition, t);
                updated = !inserted;
                break;
            case LOAD:
                inserted = load(db, partition, t);
            }

            if (inserted && tableDefinition.hasHistogram()) {
                addHistogram(db, t);
            }
            if (updated && tableDefinition.hasHistogram()) {
                // TODO updateHistogram(t);
            }
            tablespace.dispose(db);
        } catch (IOException | RocksDBException e) {
            log.error("failed to insert a record: ", e);
            YamcsServer.getCrashHandler(ydb.getYamcsInstance()).handleCrash("Archive",
                    "failed to insert a record in " + tableDefinition.getName() + ": " + e);
        }

    }
    private boolean load(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
        byte[] v = tableDefinition.serializeValue(t);

        db.put(k, v);
        return true;
    }
    
    private boolean insert(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
        byte[] v = tableDefinition.serializeValue(t);

        if (db.get(k) == null) {
            db.put(k, v);
            return true;
        } else {
            return false;
        }
    }

    private boolean upsert(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
        byte[] v = tableDefinition.serializeValue(t);
        if (db.get(k) == null) {

            db.put(k, v);
            return true;
        } else {
            db.put(k, v);
            return false;
        }
    }

    /**
     * returns true if a new record has been inserted and false if an record was
     * already existing with this key (even if modified)
     * 
     * @param partition
     * @throws RocksDBException
     */
    private boolean insertAppend(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] k = getPartitionKey(partition, tableDefinition.serializeKey(t));
        byte[] v = db.get(k);
        boolean inserted = false;
        if (v != null) {// append to an existing row
            Tuple oldt = tableDefinition.deserialize(k, v);
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
                db.put(k, v);
            }
        } else {// new row
            inserted = true;
            v = tableDefinition.serializeValue(t);
            db.put(k, v);
        }
        return inserted;
    }

    private boolean upsertAppend(YRDB db, RdbPartition partition, Tuple t) throws RocksDBException {
        byte[] dbKey = getPartitionKey(partition, tableDefinition.serializeKey(t));

        byte[] v = db.get(dbKey);
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
                db.put(dbKey, v);
            }
        } else {// new row
            inserted = true;
            v = tableDefinition.serializeValue(t);
            db.put(dbKey, v);
        }
        return inserted;
    }

    // prepends the partition binary value to the key
    private byte[] getPartitionKey(RdbPartition partition, byte[] k) {
        byte[] pk = ByteArrayUtils.encodeInt(partition.tbsIndex, new byte[4 + k.length], 0);
        System.arraycopy(k, 0, pk, 4, k.length);
        return pk;
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

    public void close() {
    }

    @Override
    public void streamClosed(Stream stream) {

    }

    protected synchronized void addHistogram(YRDB db, Tuple t) throws IOException, RocksDBException {
        List<String> histoColumns = tableDefinition.getHistogramColumns();
        for (String columnName : histoColumns) {
            if (!t.hasColumn(columnName)) {
                continue;
            }
            long time = (Long) t.getColumn(0);
            RdbHistogramInfo histo = (RdbHistogramInfo) partitionManager.createAndGetHistogram(time, columnName);
            ColumnSerializer cs = tableDefinition.getColumnSerializer(columnName);
            byte[] v = cs.toByteArray(t.getColumn(columnName));
            addHistogramForColumn(db, histo.tbsIndex, v, time);
        }
    }

    private void addHistogramForColumn(YRDB db, int histoTbsIndex, byte[] columnv, long time) throws RocksDBException {
        long sstart = time / HistogramSegment.GROUPING_FACTOR;
        int dtime = (int) (time % HistogramSegment.GROUPING_FACTOR);

        HistogramSegment segment;
        byte[] key = HistogramSegment.key(sstart, columnv);
        byte[] val = db.get(dbKey(histoTbsIndex, key));
        if (val == null) {
            segment = new HistogramSegment(columnv, sstart);
        } else {
            segment = new HistogramSegment(columnv, sstart, val);
        }

        segment.merge(dtime);

        byte[] k = dbKey(histoTbsIndex, segment.key());
        db.put(k, segment.val());
    }
}