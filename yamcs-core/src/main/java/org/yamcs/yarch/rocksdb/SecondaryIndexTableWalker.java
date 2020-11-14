package org.yamcs.yarch.rocksdb;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.WriteBatch;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.DataType._type;

/**
 * iterates through a table based on the secondary index range.
 * 
 * <p>
 * The Rocksdb key of the secondary index is formed by
 * <ul>
 * <li>tbsIndex</li>
 * <li>secondary key</li>
 * <li>primary key</li>
 * </ul>
 * 
 * 
 * 
 * 
 * @author nm
 *
 */
public class SecondaryIndexTableWalker implements TableWalker {
    private final RdbTable table;
    private final Tablespace tablespace;

    DbRange skRange;
    DbRange pkRange;

    /**
     * When iterating through the table we do not need to decode (transform to column values) the primary or the
     * secondary key but we need to extract the primary key to be able to retrieve the row from the main table.
     * <p>
     * The primary key is located in the RocksDB key after the secondary key, so we need to know the length of the
     * secondary key to be able to extract the primary key.
     * <p>
     * The secondary key can contain strings and thus be of variable length. The array below contains the length of each
     * fixed group of columns in between the string columns. For the string columns, the null termination has to be
     * searched.
     * <p>
     * For example if the secondary index has no string column, this array contains only one element - the length of the
     * secondary key.
     */
    final int skeyLength[];

    boolean batchUpdates = false;
    Snapshot snapshot = null;
    protected TableVisitor visitor;

    volatile boolean running = true;

    protected SecondaryIndexTableWalker(Tablespace tablespace, RdbTable table,
            boolean ascending, boolean follow) {
        this.tablespace = tablespace;
        this.table = table;

        IntArray a = new IntArray();
        TableDefinition tblDef = table.getDefinition();

        List<String> sindex = tblDef.getSecondaryIndex();
        int offset = 0;
        for (String s : sindex) {
            TableColumnDefinition tcd = tblDef.getColumnDefinition(s);
            int size = DataType.getSerializedSize(tcd.getType());
            if (size > 0) {
                offset += size + 1; // +1 is from the datatype byte, see SecondaryIndexWriter#addTuple
            } else {
                assert (tcd.getType().val == _type.STRING);
                a.add(offset + 1);
                offset = 0;
            }
        }
        a.add(offset);

        this.skeyLength = a.toArray();
    }

    /**
     * 
     * Iterate data through the given interval taking into account also the tableRange.
     * <p>
     * tableRange has to be non-null but can be unbounded at one or both ends.
     * <p
     * Return true if the tableRange is bounded and the end has been reached.
     */
    public void walk(TableVisitor visitor) throws YarchException {
        this.visitor = visitor;
        int tbsIndex = table.getSecondaryIndexWriter().getTbsIndex();
        DbRange dbRange = RdbTableWalker.getDbRange(tbsIndex, skRange);
        YRDB rdb = tablespace.getRdb();
        try (ReadOptions readOptions = new ReadOptions();
                RocksIterator rocksIt = rdb.getDb().newIterator(readOptions);
                AscendingRangeIterator it = new AscendingRangeIterator(rocksIt, dbRange)) {
            while (isRunning() && it.isValid()) {
                byte[] dbKey = it.key();
                byte[] pk = getPk(dbKey);
                if (pkInRange(pk)) {
                    visitRow(pk, it.value());
                }
                it.next();
            }
        }
    }

    private void visitRow(byte[] pk, byte[] skValue) {
        String part = null;
        int rowTbsIndex = ByteArrayUtils.decodeInt(skValue, 0);
        if (skValue.length > 4) {
            part = new String(skValue, 4, skValue.length - 4, StandardCharsets.US_ASCII);
        }
        YRDB rdb = null;
        try {
            rdb = tablespace.getRdb(part);
            byte[] dbKey = RdbStorageEngine.dbKey(rowTbsIndex, pk);
            byte[] rowValue = rdb.get(dbKey);
            if (rowValue != null) {
                TableVisitor.Action action = visitor.visit(pk, rowValue);
                executeAction(rdb, action, dbKey);
            }
        } catch (RocksDBException e) {
            throw new YarchException(e);
        } finally {
            if (rdb != null) {
                tablespace.dispose(rdb);
            }
        }
    }

    private boolean pkInRange(byte[] pk) {
        return (pkRange == null) || (ByteArrayUtils.compare(pkRange.rangeStart, pk) >= 0
                && ByteArrayUtils.compare(pkRange.rangeEnd, pk) <= 0);
    }

    private boolean isRunning() {
        return running;
    }

    byte[] getPk(byte[] dbkey) {
        int offset = RdbStorageEngine.TBS_INDEX_SIZE;

        try {
            int i;
            for (i = 0; i < skeyLength.length - 1; i++) {
                offset += skeyLength[i];
                while (dbkey[offset] != 0) {// skip null terminated string
                    offset++;
                }
                offset++;
            }

            offset += skeyLength[i];
            return Arrays.copyOfRange(dbkey, offset, dbkey.length);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new DatabaseCorruptionException(
                    "Cannot decode secondary index key " + StringConverter.arrayToHexString(dbkey));
        }
    }

    void executeAction(WriteBatch writeBatch, TableVisitor.Action action, byte[] dbKey)
            throws RocksDBException {
        if (action.action() == TableVisitor.ActionType.DELETE) {
            writeBatch.delete(dbKey);
        } else if (action.action() == TableVisitor.ActionType.UPDATE) {
            writeBatch.delete(dbKey);
        }
        if (action.stop()) {
            close();
        }
    }

    void executeAction(YRDB rdb, TableVisitor.Action action, byte[] dbKey)
            throws RocksDBException {
        if (action.action() == TableVisitor.ActionType.DELETE) {
            rdb.delete(dbKey);
        } else if (action.action() == TableVisitor.ActionType.UPDATE) {
            rdb.put(dbKey, action.getUpdateValue());
        }
        if (action.stop()) {
            close();
        }
    }

    @Override
    public void setPrimaryIndexRange(DbRange pkRange) {
        this.pkRange = pkRange;
    }

    @Override
    public void setSecondaryIndexRange(DbRange skRange) {
        this.skRange = skRange;
    }

    @Override
    public void close() {
        running = false;
    }
}
