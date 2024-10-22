package org.yamcs.yarch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.LimitExceededException;
import org.yamcs.utils.ByteArray;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.IndexedList;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.streamsql.ColumnNotFoundException;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;

/**
 * A table definition consists of a (key,value) pair of tuple definitions. A tuple has to contain all the columns from
 * the key while it can contain only a few of the columns from the value (basically it's a sparse table).
 * 
 * <p>
 * The key is encoded as a byte array of all the columns in order. The value is encoded as a byte array of all the
 * columns preceded by the id of their data type (1 byte) and their index (3 bytes).
 * <p>
 * The secondary index key is encoded as a byte array of all the columns in order preceded by the id of their data type
 * with the first bit set to 1 for the columns present and 0 for the column not present (i.e. null).
 * <p>
 * A table can also be partitioned according to the partitioningSpec.
 * 
 */
public class TableDefinition {
    static Logger log = LoggerFactory.getLogger(TableDefinition.class.getName());
    static final int MAX_NUM_COLS = 0x00FFFFFF;
    /*
     * table version history
     * 0: yamcs version < 3.0
     * 1: - the histogram were stored in a separate rocksdb database.
     * - pp table contained a column ppgroup instead of group
     * 2: - the PROTOBUF(org.yamcs.protobuf.Pvalue$ParameterValue) is replaced by PARAMETER_VALUE in the pp table
     * 3: November 2020 (Yamcs 5.3)
     * - changed serialization to preserve order of negative values in the key
     * - first of the 4 bytes column index preceding the value is the datatype
     * 
     */
    public static final int CURRENT_FORMAT_VERSION = 3;
    private final int formatVersion;

    // the definition of keys and values columns
    private volatile IndexedList<String, TableColumnDefinition> keyDef;
    private volatile IndexedList<String, TableColumnDefinition> valueDef;

    // these are all columns used in histograms or secondary indices
    private volatile IndexedList<String, TableColumnDefinition> histoIdx;

    // keyDef+valueDef
    private volatile TupleDefinition tupleDef;

    private YarchDatabaseInstance ydb;

    // compressed and column family name are actually storage dependent
    // if we ever support a secondary storage, we should move them into some sort of options
    private boolean compressed;
    private String cfName;

    private PartitioningSpec partitioningSpec = PartitioningSpec.noneSpec();

    private String storageEngineName = YarchDatabase.RDB_ENGINE_NAME;

    private String name;
    private List<String> histoColumns;
    private List<String> secondaryIndex;

    // these are the value columns which are autoincrement.
    private List<TableColumnDefinition> autoIncrementValues;

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

            TableColumnDefinition tcd = getTcd(cd);
            keyDef.add(cd.getName(), tcd);
            tcd.setSerializer(ColumnSerializerFactory.getColumnSerializer(this, tcd));
        }

        valueDef = new IndexedList<>(tdef.size() - keyDef.size());
        for (ColumnDefinition cd : tdef.getColumnDefinitions()) {
            if (!keyDef.hasKey(cd.getName())) {
                TableColumnDefinition tcd = getTcd(cd);
                valueDef.add(cd.getName(), tcd);
                tcd.setSerializer(ColumnSerializerFactory.getColumnSerializer(this, tcd));
            }
        }
        computeTupleDef();
        computeAutoincrValues();
        computeHistoIdx();
    }

    private TableColumnDefinition getTcd(ColumnDefinition cd) {
        if (cd instanceof TableColumnDefinition) {
            return (TableColumnDefinition) cd;
        } else {
            return new TableColumnDefinition(cd);
        }
    }

    /**
     * Used when creating the table from the serialized data on disk
     * 
     */
    public TableDefinition(int formatVersion, List<TableColumnDefinition> key, List<TableColumnDefinition> value) {

        this.keyDef = new IndexedList<>(key.size());
        this.formatVersion = formatVersion;
        for (TableColumnDefinition tcd : key) {
            tcd.setSerializer(ColumnSerializerFactory.getColumnSerializer(this, tcd));
            keyDef.add(tcd.getName(), tcd);
        }

        this.valueDef = new IndexedList<>(key.size());
        for (TableColumnDefinition tcd : value) {
            tcd.setSerializer(ColumnSerializerFactory.getColumnSerializer(this, tcd));
            valueDef.add(tcd.getName(), tcd);
        }
        computeTupleDef();
        computeAutoincrValues();
        computeHistoIdx();
    }

    public void setDb(YarchDatabaseInstance ydb) {
        this.ydb = ydb;
    }

    private void computeAutoincrValues() {
        for (TableColumnDefinition tcd : valueDef) {
            if (tcd.isAutoIncrement()) {
                if (autoIncrementValues == null) {
                    autoIncrementValues = new ArrayList<TableColumnDefinition>();
                }
                autoIncrementValues.add(tcd);
            }
        }
    }

    /**
     * time based partitions can be on the first column of the key (which has to be of type timestamp) value based
     * partitions can be on any other mandatory column
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
        TupleDefinition tmp = new TupleDefinition();
        for (ColumnDefinition cd : keyDef) {
            tmp.addColumn(cd);
        }
        for (ColumnDefinition cd : valueDef) {
            tmp.addColumn(cd);
        }
        tupleDef = tmp;
    }

    private void computeHistoIdx() {
        IndexedList<String, TableColumnDefinition> tmp = new IndexedList<>();
        TableColumnDefinition tcd = keyDef.get(0);
        if (histoColumns != null) {
            tmp.add(tcd.getName(), tcd);

            for (String s : histoColumns) {
                if (!tmp.hasKey(s)) {
                    tmp.add(s, getColumnDefinition(s));
                }
            }
        }
        if (secondaryIndex != null) {
            for (String s : secondaryIndex) {
                if (!tmp.hasKey(s)) {
                    tmp.add(s, getColumnDefinition(s));
                }
            }
        }

        histoIdx = tmp;
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
     * Checks that the table definition is valid: - primary key not string, except for the last in the list (otherwise
     * the binary sorting does not work properly)
     * 
     * @throws StreamSqlException
     */
    public void validate() throws StreamSqlException {
        for (TableColumnDefinition tcd : keyDef) {
            if (tcd.isAutoIncrement() && tcd.getType() != DataType.LONG) {
                throw new StreamSqlException(ErrCode.NOT_SUPPORTED,
                        "AUTO_INCREMENT is only supported for columns of type long.");
            }
        }

        for (TableColumnDefinition tcd : valueDef) {
            if (tcd.isAutoIncrement() && tcd.getType() != DataType.LONG) {
                throw new StreamSqlException(ErrCode.NOT_SUPPORTED,
                        "AUTO_INCREMENT is only supported for columns of type long.");
            }
        }

    }

    /**
     * Generate a new table row by transforming the key part of the tuple into a byte array to be written to disk. The
     * tuple must contain each column from the key and they are written in order (such that sorting is according to the
     * definition of the primary key).
     * <p>
     * In addition, it stores into the returned row all the values for the columns used in histograms or indices
     * 
     * @param t
     * @return a tuple containing the histogram and secondary index values as well as the generated key
     * @throws YarchException
     */
    public Row generateRow(Tuple t) throws YarchException {
        Row tableTuple = new Row(histoIdx);
        ByteArray byteArray = new ByteArray();
        for (int keyIdx = 0; keyIdx < keyDef.size(); keyIdx++) {
            TableColumnDefinition tableCd = keyDef.get(keyIdx);
            String colName = tableCd.getName();
            int tIdx = t.getColumnIndex(colName);
            Object value;
            if (tIdx < 0) {
                if (tableCd.isAutoIncrement()) {
                    value = tableCd.getSequence().next();
                } else {
                    throw new IllegalArgumentException("Tuple does not have mandatory column '" + colName + "'");
                }
            } else {
                ColumnDefinition tupleCd = t.getColumnDefinition(tIdx);
                Object v = t.getColumn(tIdx);
                value = DataType.castAs(tupleCd.type, tableCd.type, v);
            }
            tableCd.serializeValue(byteArray, value);
            setSertupleValue(tableTuple, colName, value);
        }
        tableTuple.setKey(byteArray.toArray());
        return tableTuple;
    }

    /**
     * adds all missing columns to the value part and serialises the table definition to disk
     */
    private synchronized void addMissingValueColumns(TupleDefinition tdef) {
        IndexedList<String, TableColumnDefinition> valueDef1 = new IndexedList<>(valueDef);
        if (valueDef.size() >= MAX_NUM_COLS) {
            throw new LimitExceededException(
                    "The number of value columns in table " + name + " has reached the maximum " + MAX_NUM_COLS);
        }

        for (int i = 0; i < tdef.size(); i++) {
            ColumnDefinition cd = tdef.getColumn(i);
            if (keyDef.hasKey(cd.getName())) {
                continue;
            }
            int cidx = valueDef.getIndex(cd.getName());
            if (cidx == -1) {
                TableColumnDefinition tcd = new TableColumnDefinition(cd);
                tcd.setSerializer(ColumnSerializerFactory.getColumnSerializer(this, tcd));
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
     * Commented out because not safe (can only be used when nobody is using the table)
     * 
     * @param oldName
     *            - old name of the column
     * @param newName
     *            - new name of the column
     * 
     *            public synchronized void renameColumn(String oldName, String newName) { if (keyDef.hasKey(oldName)) {
     *            keyDef.changeKey(oldName, newName); } else if (valueDef.hasKey(oldName)) { valueDef.changeKey(oldName,
     *            newName); } else { throw new IllegalArgumentException("no column named '" + oldName + "'"); }
     * 
     *            if(secondaryIndexDef.hasKey(oldName)) { keyDef.changeKey(oldName, newName); }
     * 
     *            if (oldName.equals(partitioningSpec.timeColumn)) { PartitioningSpec newSpec = new
     *            PartitioningSpec(partitioningSpec.type, newName, partitioningSpec.valueColumn);
     *            newSpec.setTimePartitioningSchema(partitioningSpec.getTimePartitioningSchema()); partitioningSpec =
     *            newSpec; } else if (oldName.equals(partitioningSpec.valueColumn)) { PartitioningSpec newSpec = new
     *            PartitioningSpec(partitioningSpec.type, partitioningSpec.timeColumn, newName);
     *            newSpec.setTimePartitioningSchema(partitioningSpec.getTimePartitioningSchema()); partitioningSpec =
     *            newSpec; }
     * 
     *            int idx = histoColumns.indexOf(oldName); if (idx != -1) { histoColumns.set(idx, newName); }
     *            ydb.saveTableDefinition(this, keyDef.getList(), valueDef.getList()); }
     */

    /**
     * Adds a value to a enum and writes the table definition to disk
     * 
     */
    private Short addEnumValue(String columnName, String value) {
        TableColumnDefinition tdef = getColumnDefinition(columnName);

        TableColumnDefinition tdef1 = new TableColumnDefinition(tdef);
        short x = tdef1.addEnumValue(value);

        IndexedList<String, TableColumnDefinition> keyDef1 = keyDef;
        IndexedList<String, TableColumnDefinition> valueDef1 = valueDef;
        IndexedList<String, TableColumnDefinition> histoIdx1 = histoIdx;

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

        idx = histoIdx.getIndex(columnName);
        if (idx >= 0) {
            histoIdx1 = new IndexedList<>(histoIdx);
            histoIdx1.set(idx, tdef1);
        }
        keyDef = keyDef1;
        valueDef = valueDef1;
        histoIdx = histoIdx1;

        computeTupleDef();

        return x;
    }

    /**
     * get the enum value corresponding to a column, creating it if it does not exist
     * 
     * @return
     */
    public Short addAndGetEnumValue(String columnName, String value) {
        TableColumnDefinition tdef = getColumnDefinition(columnName);
        if (tdef == null) {
            throw new IllegalArgumentException("No column named '" + columnName + "'");
        }
        if (value == null) {
            throw new NullPointerException("Enum value cannot be null");
        }

        Short enumValue = tdef.getEnumIndex(value);
        if (enumValue == null) {
            synchronized (this) {
                enumValue = tdef.getEnumIndex(value);
                if (enumValue == null) {
                    enumValue = addEnumValue(columnName, value);
                }
            }
        }
        return enumValue;
    }

    /**
     * Same as {@link #serializeValue(Tuple, Row)} but encodes the output in user provided byte array
     * 
     * @param tuple
     * @param sertuple
     * @param byteArray
     */
    public void serializeValue(Tuple tuple, Row sertuple, ByteArray byteArray) {
        TupleDefinition tdef = tuple.getDefinition();
        int length = byteArray.size();

        for (int i = 0; i < tdef.size(); i++) {
            ColumnDefinition tupleCd = tdef.getColumn(i);
            if (keyDef.hasKey(tupleCd.getName())) {
                continue;
            }
            int cidx = valueDef.getIndex(tupleCd.getName());
            if (cidx == -1) { // call again this function after adding the
                              // missing columns to the table
                addMissingValueColumns(tdef);
                byteArray.reset(length);
                serializeValue(tuple, sertuple, byteArray);
                return;
            }
            TableColumnDefinition tableCd = valueDef.get(cidx);
            Object v = tuple.getColumn(i);
            if (v == null) {
                continue;
            }
            Object v1 = DataType.castAs(tupleCd.type, tableCd.type, v);
            cidx = (tableCd.type.getTypeId() << 24) | cidx;
            byteArray.addInt(cidx);
            tableCd.serializeValue(byteArray, v1);

            setSertupleValue(sertuple, tupleCd.getName(), v1);
        }

        // add values for all the autoincrements which are not part of the tuple
        if (autoIncrementValues != null) {
            for (TableColumnDefinition tcd : autoIncrementValues) {
                if (!tuple.hasColumn(tcd.getName())) {
                    long v = tcd.getSequence().next();
                    int cidx = (tcd.type.getTypeId() << 24) | valueDef.getIndex(tcd.getName());
                    byteArray.addInt(cidx);
                    tcd.serializeValue(byteArray, v);
                    setSertupleValue(sertuple, tcd.getName(), v);
                }
            }
        }

        // add a final -1 eof marker
        byteArray.addInt(-1);

    }

    /**
     * Transform the value part of the tuple into a byte array to be written on disk. Each column is preceded by a tag
     * (the column index).
     * <p>
     * If there are columns in the tuple which are not in the valueDef, they are added and the TableDefinition is
     * serialized on disk.
     * <p>
     * Columns whose values are null are not serialized but their definition is still added to the table definition if
     * not present already.
     * 
     * @param tuple
     * @param sertuple
     *            - if not null, store all the values of the columns to this tuple as written to the database (possibly
     *            after some data casting)
     * @return the serialized version of the value part of the tuple
     * 
     */
    public byte[] serializeValue(Tuple tuple, Row sertuple) {
        ByteArray byteArray = new ByteArray();
        serializeValue(tuple, sertuple, byteArray);
        return byteArray.toArray();
    }

    private void setSertupleValue(Row sertuple, String colName, Object value) {
        if (sertuple != null) {
            int idx = sertuple.getIndex(colName);
            if (idx >= 0) {
                sertuple.set(idx, value);
            }
        }
    }

    public Tuple deserialize(byte[] k, byte[] v) {
        TupleDefinition tdef = new TupleDefinition();
        ArrayList<Object> cols = new ArrayList<>();
        ByteArray byteArray = ByteArray.wrap(k);

        try {
            // deserialize the key
            for (TableColumnDefinition tcd : keyDef) {
                tdef.addColumn(tcd);
                cols.add(tcd.deserializeValue(byteArray));
            }

            // deserialize the value
            byteArray = ByteArray.wrap(v);
            while (true) {
                int cidx = byteArray.getInt(); // column index
                if (cidx == -1) {
                    break;
                }
                byte dt = (byte) (cidx >>> 24);
                cidx &= 0xFFFFFF;
                if (cidx >= valueDef.size()) {
                    throw new DatabaseCorruptionException("Reference to index " + cidx
                            + " found in table" + name + " but the table definition does not have this column");
                }

                TableColumnDefinition tcd = valueDef.get(cidx);
                if (formatVersion >= 3 && tcd.getType().getTypeId() != dt) {
                    throw new DatabaseCorruptionException(String.format(
                            "Data type for table %s, column %s (id: %d) does not match the data read: expected %d, read: %d",
                            name, tcd.getName(), cidx, tcd.getType().getTypeId(), dt));
                }

                Object o = tcd.deserializeValue(byteArray);
                tdef.addColumn(tcd);
                cols.add(o);
            }
        } catch (IOException e) {
            throw new DatabaseCorruptionException(
                    "Cannot deserialize row from " + name + " "
                            + "(key:" + StringConverter.byteBufferToHexString(ByteBuffer.wrap(k))
                            + ", value: " + StringConverter.byteBufferToHexString(ByteBuffer.wrap(v)) + ")",
                    e);
        }

        return new Tuple(tdef, cols);
    }

    public boolean isCompressed() {
        return compressed;
    }

    /**
     * 
     * returns column family name (RocksDB specific) where the table is stored.
     * <p>
     * Null means to use the default (which is actually called "default" in RocksDB)
     */
    public String getCfName() {
        return cfName;
    }

    /**
     * sets the column family name (RocksDB specific)
     */
    public void setCfName(String cfName) {
        this.cfName = cfName;
    }

    /**
     * @param cname
     *            the column name
     * @return true if cname is the first column of the key
     */
    public boolean isIndexedByKey(String cname) {
        return keyDef.getIndex(cname) == 0;
    }

    /**
     * Returns the column definition for the given column or null if there is no such column
     * 
     */
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
        computeHistoIdx();
    }

    public void setSecondaryIndex(List<String> index) throws StreamSqlException {
        if (index.isEmpty()) {
            return;
        }

        for (String col : index) {
            if (!tupleDef.hasColumn(col))
                throw new StreamSqlException(ErrCode.INVALID_INDEX_COLUMN,
                        "Invalid column specified for index: " + col);

            TableColumnDefinition tcd = keyDef.get(col);
            if (tcd == null) {
                tcd = valueDef.get(col);
            }
        }
        for (int i = 0; i < index.size() - 1; i++) {
            String columnName = index.get(i);
            ColumnDefinition cd = tupleDef.getColumn(columnName);
            if (DataType.getSerializedSize(cd.getType()) < 0) {
                throw new GenericStreamSqlException(
                        "Secondary index on column " + columnName + " of type " + cd.getType()
                                + " not supported except on the last position");
            }
        }
        secondaryIndex = index;
        computeHistoIdx();
    }

    public boolean hasHistogram() {
        return histoColumns != null;
    }

    public BiMap<String, Short> getEnumValues(String columnName) {
        TableColumnDefinition tcd = getColumnDefinition(columnName);
        if (tcd == null) {
            return null;
        }
        return tcd.getEnumValues();
    }

    public List<String> getHistogramColumns() {
        return histoColumns;
    }

    public List<String> getSecondaryIndex() {
        return secondaryIndex;
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

    public boolean isPartitionedByTime() {
        return partitioningSpec.timeColumn != null;
    }

    public IndexedList<String, TableColumnDefinition> getHistoIdx() {
        return histoIdx;
    }

    public boolean hasSecondaryIndex() {
        return secondaryIndex != null;
    }

    /**
     * Return true if the table is partitioned and the colName is used as partition column (either time or value)
     * 
     * @param colName
     * @return
     */
    public boolean isPartitionedBy(String colName) {
        return colName.equals(partitioningSpec.timeColumn)
                || colName.equals(partitioningSpec.valueColumn);
    }

}
