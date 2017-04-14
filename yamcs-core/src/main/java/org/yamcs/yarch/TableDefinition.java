package org.yamcs.yarch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.ColumnSerializerFactory.EnumColumnSerializer;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.streamsql.ColumnNotFoundException;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.NotSupportedException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * A table definition consists of a (key,value) pair of tuple definitions.
 * A tuple has to contain all the columns from the key while it can contain only a few of the columns from the value
 *  (basically it's a sparse table).
 *  
 *  
 *  The key is encoded as a bytestream of all the columns in order
 *  The value is encoded as a bytestream of all the columns prceded by their index.
 * 
 * 
 * A table can also be partitioned in multiple files on disc, according to the partitioningSpec.
 * @author nm
 *
 */
public class TableDefinition {
    static Logger log = LoggerFactory.getLogger(TableDefinition.class.getName());

    /* table version history
    /* 0: yamcs version < 3.0 
     * 1: - the histogram were stored in a separate rocksdb database.
     *    - pp table contained a column ppgroup instead of group     
     * 2: - the PROTOBUF(org.yamcs.protobuf.Pvalue$ParameterValue) is replaced by PARAMETER_VALUE in the pp table 
     * 
     *  To switch to the latest version, use the bin/yamcs archive --upgrade command
     */
    public static final int CURRENT_FORMAT_VERSION = 2;
    private int formatVersion = CURRENT_FORMAT_VERSION;

    //used for rocksdb - IN_KEY means storing the partition in front of the key
    //                 - COLUMN_FAMILY : store data for each partition in a different column family
    //this is used only if the table is partitioned by value
    public enum PartitionStorage {IN_KEY, COLUMN_FAMILY}

    private PartitionStorage partitionStorage = PartitionStorage.IN_KEY;

    private final TupleDefinition keyDef;

    //the definition of all the value columns that the table can have. A particular row can have less columns
    //We have two references, one that is written to disk as part of the serialization and the other one that is actually used
    //we do this in order to prevent that a column is used before the serialization has been flushed to disk
    TupleDefinition serializedValueDef = new TupleDefinition();
    private volatile TupleDefinition valueDef = serializedValueDef;

    //   keyDef+valueDef
    private volatile TupleDefinition tupleDef; 


    private YarchDatabase ydb; 

    private boolean customDataDir=false; //if not null, dataDir represents a directory different than the YarchDatabase root. 
    //It will not be discarded after serialisation.
    private String dataDir; 

    private boolean compressed;
    private PartitioningSpec partitioningSpec = PartitioningSpec.noneSpec();

    private String storageEngineName = YarchDatabase.RDB_ENGINE_NAME;

    transient private String name; //we make this transient such that tables names can be changed by changing the filename
    private List<String> histoColumns;

    private List<ColumnSerializer<?>> keySerializers=new ArrayList<ColumnSerializer<?>>();
    private List<ColumnSerializer<?>> valueSerializers=new ArrayList<ColumnSerializer<?>>();

    //mapping from String to short for the columns of type enum
    Map<String, BiMap<String,Short>> serializedEmumValues;
    private volatile Map<String, BiMap<String,Short>> enumValues;

    /**
     * Used when creating an "empty"(i.e. no enum values) table via sql. 
     * @param name
     * @param tdef
     * @param primaryKey
     * @throws StreamSqlException
     */
    public TableDefinition(String name, TupleDefinition tdef, List<String> primaryKey) throws StreamSqlException {
        keyDef = new TupleDefinition();
        this.name=name;
        for(String s:primaryKey) {
            ColumnDefinition c=tdef.getColumn(s);
            if(c==null) {
                throw new ColumnNotFoundException(s);
            }
            keyDef.addColumn(c);
            keySerializers.add(ColumnSerializerFactory.getColumnSerializer(this, c));
        }
        for(ColumnDefinition c:tdef.getColumnDefinitions()) {
            if(keyDef.getColumn(c.getName())==null) {
                valueDef.addColumn(c);
                valueSerializers.add(ColumnSerializerFactory.getColumnSerializer(this, c));
            }
        }
        computeTupleDef();
    }

    /**
     * Used when creating the table from the def file on disk
     * @param keyDef
     * @param valueDef
     * @param enumValues
     */
    TableDefinition(TupleDefinition keyDef, TupleDefinition valueDef, Map<String, BiMap<String,Short>> enumValues) {
        this.valueDef = valueDef;
        this.serializedValueDef = valueDef;
        this.keyDef = keyDef;
        computeTupleDef();
        this.enumValues = enumValues;
        this.serializedEmumValues = enumValues;

        for(ColumnDefinition cd:keyDef.getColumnDefinitions()) {
            ColumnSerializer<?> cs = ColumnSerializerFactory.getColumnSerializer(this, cd);
            keySerializers.add(cs);
            if((cd.getType()==DataType.ENUM) && enumValues.containsKey(cd.getName())) {
                ((EnumColumnSerializer)cs).setEnumValues(enumValues.get(cd.getName()));
            }
        }
        for(ColumnDefinition cd:valueDef.getColumnDefinitions()) {
            ColumnSerializer<?> cs = ColumnSerializerFactory.getColumnSerializer(this, cd);
            valueSerializers.add(cs);
            if((cd.getType()==DataType.ENUM) && enumValues.containsKey(cd.getName())) {
                ((EnumColumnSerializer)cs).setEnumValues(enumValues.get(cd.getName()));
            }
        }
    }

    public void setDb(YarchDatabase ydb) {
        this.ydb=ydb;
    }


    /**
     * time based partitions can be on the first column of the key (which has to be of type timestamp)
     * value based partitions can be on any other mandatory column
     * @param pspec
     */
    public void setPartitioningSpec(PartitioningSpec pspec) throws StreamSqlException {
        if((pspec.type==PartitioningSpec._type.TIME) || 
                (pspec.type==PartitioningSpec._type.TIME_AND_VALUE)){
            ColumnDefinition cd=keyDef.getColumn(pspec.timeColumn);
            if(cd==null) {
                throw new GenericStreamSqlException("time partition specified on a column not part of the primary key: '"+pspec.timeColumn+"'");
            }
            if(cd.getType()!=DataType.TIMESTAMP) {
                throw new GenericStreamSqlException("time partition specified on a column of type "+cd.getType());
            }
            if(!keyDef.getColumn(0).getName().equals(pspec.timeColumn)){
                throw new GenericStreamSqlException("time partition supported only on the first column of the primary key");
            }
        }

        if((pspec.type==PartitioningSpec._type.VALUE) || 
                (pspec.type==PartitioningSpec._type.TIME_AND_VALUE)) {
            ColumnDefinition c;

            if(keyDef.hasColumn(pspec.valueColumn)) {
                c = keyDef.getColumn(pspec.valueColumn);
            } else if(valueDef.hasColumn(pspec.valueColumn)) {
                c = valueDef.getColumn(pspec.valueColumn);
            } else {
                throw new ColumnNotFoundException(pspec.valueColumn);
            }
            pspec.setValueColumnType(c.getType());
        }

        this.partitioningSpec=pspec;
    }

    private void computeTupleDef() {
        tupleDef=new TupleDefinition();
        for(ColumnDefinition cd:keyDef.getColumnDefinitions()) {
            tupleDef.addColumn(cd);
        }
        for(ColumnDefinition cd:valueDef.getColumnDefinitions()) {
            tupleDef.addColumn(cd);
        }
    }

    public TupleDefinition getKeyDefinition() {
        return keyDef;
    }

    public TupleDefinition getValueDefinition() {
        return valueDef;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name=name;
    }

    /**
     * sets the customDataDir 
     *   - if true, the dataDir will not be discarded after serialisation, so the next time the server is restarted it will stay to the set value.
     *   - if false, at restart the dataDir will be set to the YarchDatabase.dataDir
     * @param customDataDir
     */
    public void setCustomDataDir(boolean customDataDir) {
        this.customDataDir = customDataDir;
    }

    public boolean hasCustomDataDir() {
        return customDataDir;
    }


    public String getDataDir() {
        return dataDir;
    }

    /**
     * sets dataDir to this value
     * @param dataDir
     */
    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public TupleDefinition getTupleDefinition() {
        return tupleDef;
    }

    /**
     * Checks that the table definition is valid:
     *   - primary key not string, except for the last in the list (otherwise the binary sorting does not work properly)
     * @throws StreamSqlException
     */
    public void validate() throws StreamSqlException{
        for(int i=0; i<keyDef.getColumnDefinitions().size()-1; i++) {
            ColumnDefinition cd=keyDef.getColumnDefinitions().get(i);
            if(cd.getType()==DataType.BINARY) {
                throw new NotSupportedException("Primary key of type binary except the last in the list (otherwise the binary sorting does not work properly)");
            }
        }

    }

    /**
     * Transforms the key part of the tuple into a byte array to be written to disk.
     *  The tuple must contain each column from the key and they are written in order (such that sorting is according to the definition of the primary key).
     * @param t
     * @return serialized key value
     */
    public byte[] serializeKey(Tuple t) {
        try ( ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            DataOutputStream dos = new DataOutputStream(baos);
            for(int i=0; i<keyDef.size(); i++) {
                ColumnSerializer cs = keySerializers.get(i);
                String colName = keyDef.getColumn(i).getName();
                Object v = t.getColumn(colName);
                if(v==null){
                    throw new IllegalArgumentException("Tuple does not have mandatory column '"+colName+"'");
                }
                cs.serialize(dos, v);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot serialize key from tuple "+t+": ", e);
        }
    }

    /**
     * adds a column to the value part and serializes the table definition to disk
     * @param cd
     */
    private synchronized void addValueColumn(ColumnDefinition cd) {
        serializedValueDef = valueDef.copy();
        serializedValueDef.addColumn(cd);
        ydb.serializeTableDefinition(this);
        valueDef = serializedValueDef;
        valueSerializers.add(ColumnSerializerFactory.getColumnSerializer(this, cd));
        computeTupleDef();
    }

    /**
     * Changes the formatVersion and serializes the table definition to disk
     * @param formatVersion new format version
     */
    public synchronized void changeFormatDefinition(int formatVersion) {
        this.formatVersion = formatVersion;
        ydb.serializeTableDefinition(this);
    }

    /**
     * Renames column and serializes the table definition to disk.
     * 
     * Should not be used when the table is in used (e.g. by a table writer or reader). 
     * 
     * @param oldName - old name of the column
     * @param newName - new name of the column
     */
    public synchronized void renameColumn(String oldName, String newName) {
        if(keyDef.hasColumn(oldName)) {
            keyDef.renameColumn(oldName, newName);
        } else if(valueDef.hasColumn(oldName)) {
            valueDef.renameColumn(oldName, newName);
        } else {
            throw new IllegalArgumentException("no column named '"+oldName+"'");
        }

        if(oldName.equals(partitioningSpec.timeColumn)) {
            PartitioningSpec newSpec = new PartitioningSpec(partitioningSpec.type, newName, partitioningSpec.valueColumn);
            newSpec.setTimePartitioningSchema(partitioningSpec.getTimePartitioningSchema());
            partitioningSpec = newSpec;
        } else if (oldName.equals(partitioningSpec.valueColumn)) {
            PartitioningSpec newSpec = new PartitioningSpec(partitioningSpec.type,  partitioningSpec.timeColumn, newName);
            newSpec.setTimePartitioningSchema(partitioningSpec.getTimePartitioningSchema());
            partitioningSpec = newSpec;
        }

        int idx = histoColumns.indexOf(oldName);
        if(idx!=-1) {
            histoColumns.set(idx, newName);
        }

        if((enumValues!=null) && (enumValues.containsKey(oldName))) {
            BiMap<String, Short> b =  enumValues.remove(oldName);
            serializedEmumValues.put(newName, b);
        }
        ydb.serializeTableDefinition(this);
        enumValues = serializedEmumValues;
    }

    /**
     * Adds a value to a enum and writes the table definition to disk
     * we first modify the serializedEnumValues to make sure that nobody else sees the new enum id
     * before the serialization is finished (i.e. flushed on disk)
     * 
     */
    synchronized void addEnumValue(EnumColumnSerializer cs, String v) {
        String columnName = cs.getColumnName();
        BiMap<String, Short> b;

        //first check if it's not already in the map
        if((enumValues!=null) && ((b=enumValues.get(columnName))!=null) && b.containsKey(v)) {
            return; 
        }

        log.debug("Adding enum value {} for {}.{}", v, name, columnName);
        serializedEmumValues = new HashMap<>();
        if(enumValues!=null) {
            serializedEmumValues.putAll(enumValues);
        }
        b = serializedEmumValues.remove(columnName);
        BiMap<String, Short> b2= HashBiMap.create();
        if(b!=null) {
            b2.putAll(b);
        }
        b2.put(v, (short)b2.size());
        serializedEmumValues.put(columnName, b2);
        ydb.serializeTableDefinition(this);
        enumValues = serializedEmumValues;
        cs.setEnumValues(b2);
    }

    /**
     * get the enum value corresponding to a column, creating it if it does not exist
     * @return
     */
    public Short addAndGetEnumValue(String columnName, String value) {
        Short enumValue;
        Short v1;
        BiMap<String, Short>  b;
        if((enumValues==null) || ((b=enumValues.get(columnName))==null) || (v1=b.get(value))==null) {
            EnumColumnSerializer cs = (EnumColumnSerializer)getColumnSerializer(columnName);
            addEnumValue(cs, value);
            enumValue = enumValues.get(columnName).get(value);
        } else {
            enumValue = v1;
        }
        return enumValue;
    }


    /**
     * Transform the value part of the tuple into a byte array to be written on disk. 
     * Each column is preceded by a tag (the column index).
     * If there are columns in the tuple which are not in the valueDef, they are added and the TableDefinition is
     * serialized on disk.
     * 
     * @param t
     * @return the serialized version of the value part of the tuple
     */
    public byte[] serializeValue(Tuple t) {
        TupleDefinition tdef=t.getDefinition();
        try ( ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            DataOutputStream dos = new DataOutputStream(baos);
            for(int i=0; i<tdef.size(); i++ ) {
                ColumnDefinition tupleCd = tdef.getColumn(i);
                if(keyDef.hasColumn(tupleCd.getName())){
                    continue;
                }
                int cidx = valueDef.getColumnIndex(tupleCd.getName());
                if(cidx==-1) { //call again this function after adding the column to the table
                    addValueColumn(tupleCd);
                    return serializeValue(t);
                }
                ColumnDefinition tableCd = valueDef.getColumn(cidx);
                Object v = t.getColumn(i);
                Object v1 = DataType.castAs(tupleCd.type, tableCd.type, v);
                ColumnSerializer tcs = valueSerializers.get(cidx);
                dos.writeInt(cidx);
                tcs.serialize(dos, v1);
            }
            //add a final -1 eof marker
            dos.writeInt(-1);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot serialize column tuple "+t+": ", e);
        }
    }

    public Tuple deserialize(byte[] k, byte[] v) {
        TupleDefinition tdef=keyDef.copy();
        ArrayList<Object> cols = new ArrayList<>();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(k));
        try {
            //deserialize the key
            for(int i=0;i<keyDef.size(); i++) {
                ColumnDefinition cd = keyDef.getColumn(i);
                ColumnSerializer cs = keySerializers.get(i);
                Object o = cs.deserialize(dis, cd);
                cols.add(o);
            }

            //deserialize the value
            dis = new DataInputStream(new ByteArrayInputStream(v));
            while(true) {
                int cidx = dis.readInt(); //column index
                if(cidx==-1) {
                    break;
                }
                if(cidx>=valueDef.size()){
                    throw new IllegalArgumentException("Reference to index "+cidx+" found but the table definition does not have this column"); 
                }

                ColumnDefinition cd = valueDef.getColumn(cidx);
                ColumnSerializer cs = valueSerializers.get(cidx);

                Object o = cs.deserialize(dis, cd);
                tdef.addColumn(cd);
                cols.add(o);
            }
        } catch (IOException e) {
            throw new DatabaseCorruptionException("cannot deserialize ("
                    +StringConverter.byteBufferToHexString(ByteBuffer.wrap(k))+ ","
                    +StringConverter.byteBufferToHexString(ByteBuffer.wrap(v))
                    +")", e);
        }

        return new Tuple(tdef, cols.toArray());
    }


    public boolean isCompressed() {
        return compressed;
    }

    /**
     * @param cname the column name
     * @return true if cname is the first column of the key
     */
    public boolean isIndexedByKey(String cname) {
        return keyDef.getColumnIndex(cname)==0;
    }

    public ColumnDefinition getColumnDefinition(String cname) {
        if(keyDef.hasColumn(cname)) {
            return keyDef.getColumn(cname);
        }
        if(valueDef.hasColumn(cname)) {
            return valueDef.getColumn(cname);
        }
        return null;
    }

    public boolean hasPartitioning() {
        return partitioningSpec!=null;
    }

    public PartitioningSpec getPartitioningSpec() {
        return partitioningSpec;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public void setHistogramColumns(List<String> histoColumns) throws StreamSqlException {
        if(keyDef.getColumn(0).getType()!=DataType.TIMESTAMP)
            throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN, "Cannot only create histogram on tables with the first column of the primary key of type TIMESTAMP");

        for(String hc:histoColumns) {
            if(keyDef.getColumn(0).equals(hc)) 
                throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN, "Cannot create histogram on the first column of the primary key");
            if(!tupleDef.hasColumn(hc)) 
                throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN, "Invalid column specified for histogram: "+hc);
        }
        this.histoColumns=histoColumns;
    }

    public boolean hasHistogram() {
        return histoColumns!=null;
    }

    public BiMap<String, Short> getEnumValues(String columnName) {
        if(enumValues==null){
            return null;
        }
        return enumValues.get(columnName);
    }

    public List<String> getHistogramColumns() {
        return histoColumns;
    }

    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append(name).
        append("(").
        append(keyDef.toString()).
        append(", ").
        append(valueDef.toString()).
        append(", primaryKey(").
        append(keyDef).
        append("))");
        return sb.toString();
    }

    public ColumnSerializer getColumnSerializer(String columnName) {
        if (keyDef.hasColumn(columnName)) {
            int idx=keyDef.getColumnIndex(columnName);
            return keySerializers.get(idx);
        } else if (valueDef.hasColumn(columnName)) {
            int idx=valueDef.getColumnIndex(columnName);
            return valueSerializers.get(idx);
        } else { 
            throw new IllegalArgumentException("Cannot find a serializer for invalid column "+columnName);
        }
    }

    public String getStorageEngineName() {
        return storageEngineName;
    }

    public void setStorageEngineName(String storageEngineName) {
        this.storageEngineName = storageEngineName;
    }

    public PartitionStorage getPartitionStorage() {
        return partitionStorage;
    }

    public void setPartitionStorage(PartitionStorage partitionStorage) {
        this.partitionStorage = partitionStorage;
    }

    public boolean isPartitionedByValue() {
        return partitioningSpec.type==_type.TIME_AND_VALUE|| partitioningSpec.type==_type.VALUE;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }
}