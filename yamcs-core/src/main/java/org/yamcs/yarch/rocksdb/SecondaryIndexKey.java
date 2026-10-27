package org.yamcs.yarch.rocksdb;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.utils.ByteArray;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.yarch.Row;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;

/** Shared secondary-key encoding for writes, backfill and stateless pagination. */
public final class SecondaryIndexKey {
    private SecondaryIndexKey() {
    }

    public static byte[] suffix(TableDefinition definition, Row row) {
        ByteArray key = new ByteArray();
        for (String name : definition.getSecondaryIndex()) {
            TableColumnDefinition column = definition.getColumnDefinition(name);
            Object value = row.get(name);
            key.add((byte) (column.getType().getTypeId() | (value == null ? 0 : 0x70)));
            if (value != null) {
                column.serializeValue(key, value);
            }
        }
        key.add(row.getKey());
        return key.toArray();
    }

    public static byte[] suffix(TableDefinition definition, Tuple tuple) {
        Row row = definition.generateRow(tuple);
        definition.serializeValue(tuple, row);
        return suffix(definition, row);
    }

    public static byte[] prefix(TableDefinition definition, Object firstColumnValue) {
        TableColumnDefinition column = definition.getColumnDefinition(definition.getSecondaryIndex().get(0));
        ByteArray key = new ByteArray();
        key.add((byte) (column.getType().getTypeId() | (firstColumnValue == null ? 0 : 0x70)));
        if (firstColumnValue != null) {
            column.serializeValue(key, firstColumnValue);
        }
        return key.toArray();
    }

    static byte[] primaryKey(TableDefinition definition, byte[] dbKey) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(dbKey);
            buffer.position(RdbStorageEngine.TBS_INDEX_SIZE);
            for (String name : definition.getSecondaryIndex()) {
                TableColumnDefinition column = definition.getColumnDefinition(name);
                byte marker = buffer.get();
                if (marker == (byte) (0x70 | column.getType().getTypeId())) {
                    column.getSerializer().deserialize(buffer, column);
                } else if (marker != column.getType().getTypeId()) {
                    throw new IllegalArgumentException("Invalid secondary-key type marker");
                }
            }
            return Arrays.copyOfRange(dbKey, buffer.position(), dbKey.length);
        } catch (RuntimeException e) {
            throw new DatabaseCorruptionException("Cannot decode secondary index key", e);
        }
    }
}
