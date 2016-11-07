package org.yamcs.yarch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.streamsql.ColumnNotFoundException;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.NotSupportedException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

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
    static Logger log=LoggerFactory.getLogger(TableDefinition.class.getName());

    private final TupleDefinition keyDef;
    
    //the definition of all the value columns that the table can have. A particular row can have less columns
    //We have two references, one that is written to disk as part of the serialization and the other one that is actually used
    //we do this in order to prevent that a column is used before the serialization has been flushed to disk
    TupleDefinition serializedValueDef=new TupleDefinition();
    private volatile TupleDefinition valueDef=serializedValueDef;
    
    //   keyDef+valueDef
    private volatile TupleDefinition tupleDef; 
    
    
    private YarchDatabase ydb; 
    
    private boolean customDataDir=false; //if not null, dataDir represents a directory different than the YarchDatabase root. 
                                         //It will not be discarded after serialisation.
    private String dataDir; 
    
    private boolean compressed;
    private PartitioningSpec partitioningSpec;
    
    private String storageEngineName=YarchDatabase.TC_ENGINE_NAME;
    
    transient private String name; //we make this transient such that tables names can be changed by changing the filename
    private List<String> histoColumns;
    
    private List<ColumnSerializer> keySerializers=new ArrayList<ColumnSerializer>();
    private List<ColumnSerializer> valueSerializers=new ArrayList<ColumnSerializer>();
    
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
        keyDef=new TupleDefinition();
        this.name=name;
        for(String s:primaryKey) {
            ColumnDefinition c=tdef.getColumn(s);
            if(c==null) throw new ColumnNotFoundException(s);
            keyDef.addColumn(c);
            keySerializers.add(new ColumnSerializer(this, c));
        }
        for(ColumnDefinition c:tdef.getColumnDefinitions()) {
            if(keyDef.getColumn(c.getName())==null) {
                valueDef.addColumn(c);
                valueSerializers.add(new ColumnSerializer(this, c));
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
    	this.valueDef=valueDef;
    	this.serializedValueDef=valueDef;
    	this.keyDef=keyDef;
    	computeTupleDef();
    	this.enumValues=enumValues;
    	this.serializedEmumValues=enumValues;
    	
    	for(ColumnDefinition cd:keyDef.getColumnDefinitions()) {
    	    ColumnSerializer cs=new ColumnSerializer(this, cd);
    	    keySerializers.add(cs);
    	    if(cd.getType()==DataType.ENUM){
    	        if(enumValues.containsKey(cd.getName())) {
    	            cs.setEnumValues(enumValues.get(cd.getName()));
    	        }
    	    }
    	}
    	for(ColumnDefinition cd:valueDef.getColumnDefinitions()) {
    	    ColumnSerializer cs=new ColumnSerializer(this, cd);
            valueSerializers.add(cs);
            if(cd.getType()==DataType.ENUM){
                if(enumValues.containsKey(cd.getName())) {
                    cs.setEnumValues(enumValues.get(cd.getName()));
                }
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
            if(cd.getType()!=DataType.TIMESTAMP) throw new GenericStreamSqlException("time partition specified on a column of type "+cd.getType());
            if(!keyDef.getColumn(0).getName().equals(pspec.timeColumn)) throw new GenericStreamSqlException("time partition supported only on the first column of the primary key");
        }

        if((pspec.type==PartitioningSpec._type.VALUE) || 
           (pspec.type==PartitioningSpec._type.TIME_AND_VALUE)) {
            ColumnDefinition c=null;
            
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
        this.dataDir=dataDir;
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
     * @return
     */
    public byte[] serializeKey(Tuple t) {
        ByteArrayDataOutput bado=ByteStreams.newDataOutput();
        for(ColumnSerializer cs:keySerializers) {
            Object v=t.getColumn(cs.getColumnName());
            try {
                cs.serialize(bado,v);
            } catch (IOException e) {
                throw new RuntimeException("Cannot serialize column "+cs,e);
            }
        }
        return bado.toByteArray();
    }
    
    /**
     * adds a column to the value part and serialize the table definition to disk
     * @param cd
     */
    private synchronized void addValueColumn(ColumnDefinition cd) {
        serializedValueDef=valueDef.copy();
        serializedValueDef.addColumn(cd);
        ydb.serializeTableDefinition(this);
        valueDef=serializedValueDef;
        valueSerializers.add(new ColumnSerializer(this, cd));
        computeTupleDef();
    }

   /**
    * Adds a value to a enum and writes the table definition to disk
    * we first modify the serializedEnumValues to make sure that nobody else sees the new enum id
    * before the serialization is finished (i.e. flushed on disk)
    * 
    */
   synchronized void addEnumValue(ColumnSerializer cs, String v) {
       String columnName=cs.getColumnName();
       BiMap<String, Short> b;
       
       //first check if it's not already in the map (can happen if two threads were concurrently calling this method)
       if((enumValues!=null) && ((b=enumValues.get(columnName))!=null) && b.containsKey(v)) return; 
       
       log.debug("Adding enum value "+v+" for "+name+"."+columnName);
       serializedEmumValues = new HashMap<String, BiMap<String, Short>>();
       if(enumValues!=null) serializedEmumValues.putAll(enumValues);
       b = serializedEmumValues.remove(columnName);
       BiMap<String, Short> b2= HashBiMap.create();
       if(b!=null) b2.putAll(b);
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
           ColumnSerializer cs=getColumnSerializer(columnName);
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
     * @return
     */
    public byte[] serializeValue(Tuple t) {
        ByteArrayDataOutput bado=ByteStreams.newDataOutput();
        TupleDefinition tdef=t.getDefinition();

        for(ColumnDefinition cd:tdef.getColumnDefinitions()) {
            if(keyDef.hasColumn(cd.getName()))continue;
            if(!valueDef.hasColumn(cd.getName())) {
                addValueColumn(cd);
                return serializeValue(t);
            }
            Integer cidx=valueDef.getColumnIndex(cd.getName());
            ColumnSerializer tcs=valueSerializers.get(cidx);

            Object v=t.getColumn(cd.getName());
            try {
                bado.writeInt(cidx);
                tcs.serialize(bado, v);
            } catch (Exception e) {
                throw new RuntimeException("Cannot serialize column "+cd+": "+e,e);
            }
        }
        
        //add a final -1 eof marker
        bado.writeInt(-1);
        return bado.toByteArray();

    }
    
    public Tuple deserialize(byte[] k, byte[] v) {
        TupleDefinition tdef=keyDef.copy();
        ArrayList<Object> cols=new ArrayList<Object>();
        ByteArrayDataInput badi=ByteStreams.newDataInput(k);

        try {
            //deserialize the key
            for(ColumnSerializer cd:keySerializers) {
                Object o=cd.deserialize(badi);
                if(o==null) return null;
                cols.add(o);
            }

            //deserialize the value
            badi=ByteStreams.newDataInput(v);
            while(true) {
                int cidx = badi.readInt(); //column index
                if(cidx==-1) break;
                if(cidx>=valueDef.size())throw new RuntimeException("Reference to index "+cidx+" found but the table definition does not have this column"); 

                ColumnDefinition cd=valueDef.getColumn(cidx);
                ColumnSerializer cs=valueSerializers.get(cidx);
             
                Object o=cs.deserialize(badi);
                if(o==null) return null;
                tdef.addColumn(cd);
                cols.add(o);
            }
        } catch (IOException e) {
            throw new RuntimeException("cannot deserialize ("
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
     * Returns true if this is the first column of the key
     * @return
     */
    public boolean isIndexedByKey(String cname) {
        return keyDef.getColumnIndex(cname)==0;
    }

    public ColumnDefinition getColumnDefinition(String cname) {
        if(keyDef.hasColumn(cname)) return keyDef.getColumn(cname);
        if(valueDef.hasColumn(cname)) return valueDef.getColumn(cname);
        return null;
    }

    public boolean hasPartitioning() {
        return (partitioningSpec!=null);
    }

    public PartitioningSpec getPartitioningSpec() {
        return partitioningSpec;
    }

    public void setCompressed(boolean compressed) {
        this.compressed=compressed;
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
        if(enumValues==null)return null;
        return enumValues.get(columnName);
    }
    
    public List<String> getHistogramColumns() {
        return histoColumns;
    }
    
    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append(name.toString()).
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
            throw new RuntimeException("Cannot find a serializer for invalid column "+columnName);
        }
    }

    public String getStorageEngineName() {
        return storageEngineName;
    }

    public void setStorageEngineName(String storageEngineName) {
        this.storageEngineName = storageEngineName;
    }
}