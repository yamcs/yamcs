package org.yamcs.yarch.rocksdb;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.yamcs.utils.ByteArray;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.Row;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchException;

/**
 * Writes secondary index for one table.
 * <p>
 * Each secondary index has its own tbsIndex.
 * <p>
 * The key is composed of a combination of secondary index columns and primary key.
 * The value is the tbsIndex followed by the partition directory (if any)
 * 
 * @author nm
 *
 */
public class SecondaryIndexWriter {
    final int tbsIndex;
    final TableDefinition tableDefinition;
    final Tablespace tablespace;
    private final List<String> columns;

    public SecondaryIndexWriter(Tablespace tablespace, TableDefinition tableDefinition, int tbsIndex) {
        this.tbsIndex = tbsIndex;
        this.tableDefinition = tableDefinition;
        this.tablespace = tablespace;
        this.columns = tableDefinition.getSecondaryIndex();
    }

    /**
     * Write the secondary index for the given row
     * <p>
     * Unlike for the primary key, null (non existent) values are allowed in the secondary index.
     * <p>
     * To do that, we prefix each column value by a byte with the first bit set to 0 for the non existent values and 1
     * for the values that do exist such that the null values are sorted before the non-null
     * <p>
     * this is mysql non-configurable behaviour, postgresql is different by default but configurable.
     * 
     * @param writeBatch
     * @param tuple
     * @param dbkey
     * @param partition
     */
    void addTuple(WriteBatch writeBatch, Row row, Partition partition) {
        RdbPartition rpart = (RdbPartition) partition;

        try (ByteArrayOutputStream baosValue = new ByteArrayOutputStream()) {
            ByteArray baKey = new ByteArray();
            DataOutputStream dosValue = new DataOutputStream(baosValue);

            baKey.addInt(tbsIndex);
            for (String colName : columns) {
                TableColumnDefinition tableCd = row.getColumnDefinition(colName);
                DataType dt = tableCd.getType();
                Object value = row.get(colName);
                if (value == null) {
                    baKey.add(dt.getTypeId());
                } else {
                    baKey.add((byte)(0x70 | dt.getTypeId()));
                    tableCd.serializeValue(baKey, value);
                }
            }
            baKey.add(row.getKey());
            byte[] dbkey = baKey.toArray();

            dosValue.writeInt(rpart.tbsIndex);
            if (rpart.dir != null) {
                byte[] part = rpart.dir.getBytes(StandardCharsets.US_ASCII);
                dosValue.write(part);
            }
            writeBatch.put(dbkey, baosValue.toByteArray());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot serialize key from tuple " + row + ": ", e);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }

    }

    public int getTbsIndex() {
        return tbsIndex;
    }
}
