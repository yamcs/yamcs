package org.yamcs.yarch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.IndexedList;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.streamsql.ColumnNotFoundException;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.NotSupportedException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;

/**
 * A table definition consists of a (key,value) pair of tuple definitions. A
 * tuple has to contain all the columns from the key while it can contain only a
 * few of the columns from the value (basically it's a sparse table).
 * 
 * <p>
 * The key is encoded as a bytestream of all the columns in order The value is
 * encoded as a bytestream of all the columns prceded by their index.
 * 
 * <p>
 * A table can also be partitioned according to the partitioningSpec.
 * 
 * @author nm
 *
 */
public class TableDefinition {
    static Logger log = LoggerFactory.getLogger(TableDefinition.class.getName());

    /*
     * table version history
     * 0: yamcs version < 3.0
     * 1: - the histogram were stored in a separate rocksdb database.
     * - pp table contained a column ppgroup instead of group
     * 2: - the PROTOBUF(org.yamcs.protobuf.Pvalue$ParameterValue) is replaced by PARAMETER_VALUE in the pp table
     * 3: November 2020 (Yamcs 5.3)
     * changed serialization to preserve order of negative values in the key
     * 
     * 
     */
    public static final int CURRENT_FORMAT_VERSION = 3;
    private final int formatVersion;

    // the definition of keys and values columns
    private volatile IndexedList<String, TableColumnDefinition> keyDef;
    private volatile IndexedList<String, TableColumnDefinition> valueDef;

    // keyDef+valueDef
    private volatile TupleDefinition tupleDef;

    private YarchDatabaseInstance ydb;

    private boolean compressed;
    private PartitioningSpec partitioningSpec = PartitioningSpec.noneSpec();

    private String storageEngineName = YarchDatabase.RDB_ENGINE_NAME;

    private String name;
    private List<String> histoColumns;

    /**
     * Used when creating an empty table via sql.
     * 
     * @param name
     * @param tdef
     * @param primaryKey
     * @throws StreamSqlException
     */
    public TableDefinition(String name, TupleDefinition tdef, List<String> primaryKey) throws StreamSqlException {
        this.name = name;
        this.formatVersion = CURRENT_FORMAT_VERSION;

        keyDef = new IndexedList<>();
        for (String s : primaryKey) {
            ColumnDefinition cd = tdef.getColumn(s);
            if (cd == null) {
                throw new ColumnNotFoundException(s);
            }
            TableColumnDefinition tcd = new TableColumnDefinition(cd);
            keyDef.add(cd.getName(), tcd);
            tcd.serializer = ColumnSerializerFactory.getColumnSerializer(this, tcd);
        }

        valueDef = new IndexedList<>(tdef.size()- keyDef.size());
        for (ColumnDefinition cd : tdef.getColumnDefinitions()) {
            if (!keyDef.hasKey(cd.getName())) {
                TableColumnDefinition tcd = new TableColumnDefinition(cd);
                valueDef.add(cd.getName(), tcd);
                tcd.serializer = ColumnSerializerFactory.getColumnSerializer(this, tcd);
            }
        }
        computeTupleDef();
    }

    /**
     * Used when creating the table from the serialized data on disk
     * 
     */
    public TableDefinition(int formatVersion, List<TableColumnDefinition> key, List<TableColumnDefinition> value) {
       
        this.keyDef = new IndexedList<>(key.size());
        this.formatVersion = formatVersion;
        for (TableColumnDefinition tcd : key) {
            tcd.serializer = ColumnSerializerFactory.getColumnSerializer(this, tcd);
            keyDef.add(tcd.getName(), tcd);
        }
        
        this.valueDef = new IndexedList<>(key.size());
        for (TableColumnDefinition tcd : value) {
            tcd.serializer = ColumnSerializerFactory.getColumnSerializer(this, tcd);
            valueDef.add(tcd.getName(), tcd);
        }
        computeTupleDef();
    }

    public void setDb(YarchDatabaseInstance ydb) {
        this.ydb = ydb;
    }

    /**
     * time based partitions can be on the first column of the key (which has to
     * be of type timestamp) value based partitions can be on any other
     * mandatory column
     * 
     * @param pspec
     */
    public void setPartitioningSpec(PartitioningSpec pspec) throws StreamSqlException {
        if ((pspec.type == PartitioningSpec._type.TIME) || (pspec.type == PartitioningSpec._type.TIME_AND_VALUE)) {
            ColumnDefinition cd = keyDef.get(pspec.timeColumn);
            if (cd == null) {
                throw new GenericStreamSqlException(
                        "time partition specified on a column not part of the primary key: '" + pspec.timeColumn + "'");
            }
            if (cd.getType() != DataType.TIMESTAMP) {
                throw new GenericStreamSqlException("time partition specified on a column of type " + cd.getType());
            }
            if (!keyDef.get(0).getName().equals(pspec.timeColumn)) {
                throw new GenericStreamSqlException(
                        "time partition supported only on the first column of the primary key");
            }
        }

        if ((pspec.type == PartitioningSpec._type.VALUE) || (pspec.type == PartitioningSpec._type.TIME_AND_VALUE)) {
            ColumnDefinition c = getColumnDefinition(pspec.valueColumn);
            if (c == null) {
                throw new ColumnNotFoundException(pspec.valueColumn);
            }
            pspec.setValueColumnType(c.getType());
        }

        this.partitioningSpec = pspec;
    }

    private void computeTupleDef() {
        tupleDef = new TupleDefinition();
        for (ColumnDefinition cd : keyDef) {
            tupleDef.addColumn(cd);
        }
        for (ColumnDefinition cd : valueDef) {
            tupleDef.addColumn(cd);
        }
    }

    public List<TableColumnDefinition> getKeyDefinition() {
        return keyDef.getList();
    }

    public List<TableColumnDefinition> getValueDefinition() {
        return valueDef.getList();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TupleDefinition getTupleDefinition() {
        return tupleDef;
    }

    /**
     * Checks that the table definition is valid: - primary key not string,
     * except for the last in the list (otherwise the binary sorting does not
     * work properly)
     * 
     * @throws StreamSqlException
     */
    public void validate() throws StreamSqlException {
        for (int i = 0; i < keyDef.size() - 1; i++) {
            ColumnDefinition cd = keyDef.get(i);
            if (cd.getType() == DataType.BINARY) {
                throw new NotSupportedException(
                        "Primary key of type binary except the last in the list (otherwise the binary sorting does not work properly)");
            }
        }

    }

    /**
     * Transforms the key part of the tuple into a byte array to be written to
     * disk. The tuple must contain each column from the key and they are
     * written in order (such that sorting is according to the definition of the
     * primary key).
     * 
     * @param t
     * @return serialized key value
     */
    public byte[] serializeKey(Tuple t) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            DataOutputStream dos = new DataOutputStream(baos);
            for (int keyIdx = 0; keyIdx < keyDef.size(); keyIdx++) {
                TableColumnDefinition tableCd = keyDef.get(keyIdx);
                String colName = tableCd.getName();
                int tIdx = t.getColumnIndex(colName);
                if (tIdx < 0) {
                    throw new IllegalArgumentException("Tuple does not have mandatory column '" + colName + "'");
                }
                ColumnDefinition tupleCd = t.getColumnDefinition(tIdx);
                Object v = t.getColumn(tIdx);
                Object v1 = DataType.castAs(tupleCd.type, tableCd.type, v);

                tableCd.serialize(dos, v1);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot serialize key from tuple " + t + ": ", e);
        }
    }

    /**
     * adds all missing columns to the value part and serialises the table definition to
     * disk
     */
    private synchronized void addMissingValueColumns(TupleDefinition tdef) {
        IndexedList<String, TableColumnDefinition> valueDef1 = new IndexedList<>(valueDef);

        for (int i = 0; i < tdef.size(); i++) {
            ColumnDefinition cd = tdef.getColumn(i);
            if (keyDef.hasKey(cd.getName())) {
                continue;
            }
            int cidx = valueDef.getIndex(cd.getName());
            if (cidx == -1) {
                TableColumnDefinition tcd = new TableColumnDefinition(cd);
                tcd.serializer = ColumnSerializerFactory.getColumnSerializer(this, tcd);
                valueDef1.add(tcd.name, tcd);
            }
        }

        ydb.saveTableDefinition(this, keyDef.getList(), valueDef1.getList());
        valueDef = valueDef1;
        computeTupleDef();
    }

    /**
     * Renames column and serializes the table definition to disk.
     * 
     * Should not be used when the table is in used (e.g. by a table writer or
     * reader).
     * 
     * @param oldName
     *            - old name of the column
     * @param newName
     *            - new name of the column
     */
    public synchronized void renameColumn(String oldName, String newName) {
        if (keyDef.hasKey(oldName)) {
            keyDef.changeKey(oldName, newName);
        } else if (valueDef.hasKey(oldName)) {
            valueDef.changeKey(oldName, newName);
        } else {
            throw new IllegalArgumentException("no column named '" + oldName + "'");
        }

        if (oldName.equals(partitioningSpec.timeColumn)) {
            PartitioningSpec newSpec = new PartitioningSpec(partitioningSpec.type, newName,
                    partitioningSpec.valueColumn);
            newSpec.setTimePartitioningSchema(partitioningSpec.getTimePartitioningSchema());
            partitioningSpec = newSpec;
        } else if (oldName.equals(partitioningSpec.valueColumn)) {
            PartitioningSpec newSpec = new PartitioningSpec(partitioningSpec.type, partitioningSpec.timeColumn,
                    newName);
            newSpec.setTimePartitioningSchema(partitioningSpec.getTimePartitioningSchema());
            partitioningSpec = newSpec;
        }

        int idx = histoColumns.indexOf(oldName);
        if (idx != -1) {
            histoColumns.set(idx, newName);
        }
        ydb.saveTableDefinition(this, keyDef.getList(), valueDef.getList());
    }

    /**
     * Adds a value to a enum and writes the table definition to disk
     * 
     */
    synchronized private Short addEnumValue(String columnName, String value) {
        TableColumnDefinition tdef = getColumnDefinition(columnName);

        TableColumnDefinition tdef1 = new TableColumnDefinition(tdef);
        short x = tdef1.addEnumValue(value);

        IndexedList<String, TableColumnDefinition> keyDef1 = keyDef;
        IndexedList<String, TableColumnDefinition> valueDef1 = valueDef;
        
        int idx = keyDef.getIndex(columnName);
        if (idx >= 0) {
            keyDef1 = new IndexedList<>(keyDef);
            keyDef1.set(idx, tdef1);
        } else {
            idx = valueDef.getIndex(columnName);
            assert (idx >= 0);
            valueDef1 = new IndexedList<>(valueDef);
            valueDef1.set(idx, tdef1);
        }
        
        ydb.saveTableDefinition(this, keyDef1.getList(), valueDef1.getList());
        keyDef = keyDef1;
        valueDef = valueDef1;

        return x;
    }

    /**
     * get the enum value corresponding to a column, creating it if it does not
     * exist
     * 
     * @return
     */
    public Short addAndGetEnumValue(String columnName, String value) {
        TableColumnDefinition tdef = getColumnDefinition(columnName);
        if (tdef == null) {
            throw new IllegalArgumentException("No column named '" + columnName + "'");
        }

        Short enumValue = tdef.getEnumIndex(value);
        if (enumValue == null) {
            enumValue = addEnumValue(columnName, value);
        }
        return enumValue;
    }

    /**
     * Transform the value part of the tuple into a byte array to be written on
     * disk. Each column is preceded by a tag (the column index). If there are
     * columns in the tuple which are not in the valueDef, they are added and
     * the TableDefinition is serialized on disk.
     * 
     * @param t
     * @return the serialized version of the value part of the tuple
     */
    public byte[] serializeValue(Tuple t) {
        TupleDefinition tdef = t.getDefinition();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            DataOutputStream dos = new DataOutputStream(baos);
            for (int i = 0; i < tdef.size(); i++) {
                ColumnDefinition tupleCd = tdef.getColumn(i);
                if (keyDef.hasKey(tupleCd.getName())) {
                    continue;
                }
                int cidx = valueDef.getIndex(tupleCd.getName());
                if (cidx == -1) { // call again this function after adding the
                                  // missing columns to the table
                    addMissingValueColumns(tdef);
                    return serializeValue(t);
                }
                TableColumnDefinition tableCd = valueDef.get(cidx);
                Object v = t.getColumn(i);
                Object v1 = DataType.castAs(tupleCd.type, tableCd.type, v);
                dos.writeInt(cidx);
                tableCd.serialize(dos, v1);
            }
            // add a final -1 eof marker
            dos.writeInt(-1);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot serialize column tuple " + t + ": ", e);
        }
    }

    public Tuple deserialize(byte[] k, byte[] v) {
        TupleDefinition tdef = new TupleDefinition();
        ArrayList<Object> cols = new ArrayList<>();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(k));
        try {
            // deserialize the key
            for (TableColumnDefinition tcd : keyDef) {
                tdef.addColumn(tcd);
                cols.add(tcd.deserialize(dis));
            }

            // deserialize the value
            dis = new DataInputStream(new ByteArrayInputStream(v));
            while (true) {
                int cidx = dis.readInt(); // column index
                if (cidx == -1) {
                    break;
                }
                if (cidx >= valueDef.size()) {
                    throw new DatabaseCorruptionException(
                            "Reference to index " + cidx + " found but the table definition does not have this column");
                }

                TableColumnDefinition tcd = valueDef.get(cidx);

                Object o = tcd.deserialize(dis);
                tdef.addColumn(tcd);
                cols.add(o);
            }
        } catch (IOException e) {
            throw new DatabaseCorruptionException(
                    "cannot deserialize (" + StringConverter.byteBufferToHexString(ByteBuffer.wrap(k)) + ","
                            + StringConverter.byteBufferToHexString(ByteBuffer.wrap(v)) + ")",
                    e);
        }

        return new Tuple(tdef, cols);
    }

    public boolean isCompressed() {
        return compressed;
    }

    /**
     * @param cname
     *            the column name
     * @return true if cname is the first column of the key
     */
    public boolean isIndexedByKey(String cname) {
        return keyDef.getIndex(cname) == 0;
    }

    public TableColumnDefinition getColumnDefinition(String colName) {
        TableColumnDefinition tcd = keyDef.get(colName);
        if (tcd != null) {
            return tcd;
        }
        return valueDef.get(colName);
    }

    public boolean hasPartitioning() {
        return partitioningSpec.type != _type.NONE;
    }

    public PartitioningSpec getPartitioningSpec() {
        return partitioningSpec;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public void setHistogramColumns(List<String> histoColumns) throws StreamSqlException {
        if (keyDef.get(0).getType() != DataType.TIMESTAMP)
            throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                    "Cannot only create histogram on tables with the first column of the primary key of type TIMESTAMP");

        for (String hc : histoColumns) {
            if (keyDef.getIndex(hc) == 0)
                throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                        "Cannot create histogram on the first column of the primary key");
            if (!tupleDef.hasColumn(hc))
                throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                        "Invalid column specified for histogram: " + hc);
        }
        this.histoColumns = histoColumns;
    }

    public boolean hasHistogram() {
        return histoColumns != null;
    }

    public BiMap<String, Short> getEnumValues(String columnName) {
        TableColumnDefinition tcd = getColumnDefinition(columnName);
        if(tcd==null) {
            return null;
        }
        return tcd.getEnumValues();
    }

    public List<String> getHistogramColumns() {
        return histoColumns;
    }

    public <T extends Object> ColumnSerializer<T> getColumnSerializer(String columnName) {
        TableColumnDefinition tcd = getColumnDefinition(columnName);
        if (tcd == null) {
            throw new IllegalArgumentException("Invalid column " + columnName);
        }
        return tcd.getSerializer();
    }

    public String getStorageEngineName() {
        return storageEngineName;
    }

    public void setStorageEngineName(String storageEngineName) {
        this.storageEngineName = storageEngineName;
    }

    public int getFormatVersion() {
        return formatVersion;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("(").append(keyDef.toString()).append(", ").append(valueDef.toString())
                .append(", primaryKey(").append(keyDef).append("))");
        return sb.toString();
    }

    /**
     * this method is used during table migration between two versions
     * <p>
     * It should not be used for other things.
     */
    public void changeDataType(String cname, DataType dataType) {
        int idx = keyDef.getIndex(cname);
        if (idx >= 0) {
            TableColumnDefinition tcd = keyDef.get(idx);
            keyDef.set(idx, new TableColumnDefinition(tcd, dataType));
        } else {
            idx = valueDef.getIndex(cname);
            if (idx >= 0) {
                TableColumnDefinition tcd = valueDef.get(idx);
                valueDef.set(idx, new TableColumnDefinition(tcd, dataType));
            } else {
                throw new IllegalArgumentException("No column named " + cname);
            }
        }
    }

    /**
     * 
     * @param colName
     * @return true if the column is part of the primary key
     */
    public boolean hasKey(String colName) {
        return keyDef.hasKey(colName);
    }
}
