package org.yamcs.yarch;

import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.yarch.DataType._type;



import com.google.common.collect.BiMap;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLite.Builder;

/**
 * Serializes column values to byte arrays (used as part of tables) and back
 * @author nm
 *
 */
public class ColumnSerializer {
    static Logger log=LoggerFactory.getLogger(ColumnSerializer.class.getName());
    
    private final ColumnDefinition cd;
    private final DataType type;
    
    //for columns of type PROTOBUF
    private final Method newBuilderMethod; 

    //for columns of type ENUM
    private volatile BiMap<String,Short> enumValues;
    static YConfiguration config;
    static int maxBinaryLength=1048576;
    private final TableDefinition tblDef;

    
    static {
        try {
            config=YConfiguration.getConfiguration("yamcs");
            if(config.containsKey("maxBinaryLength")) {
            	maxBinaryLength=config.getInt("maxBinaryLength");
            }
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
        
    } 

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public ColumnSerializer(TableDefinition tblDef, ColumnDefinition cd) {
        this.cd=cd;
        this.tblDef=tblDef;
        type=cd.getType();
        
        if(type.val==_type.PROTOBUF) {
            Class c;
            try {
                c = Class.forName(type.getClassName());
                newBuilderMethod=c.getMethod("newBuilder");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot find class '"+type.getClassName()+"' required to deserialize column '"+cd.getName()+"'");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Class '"+type.getClassName()+"' required to deserialize column '"+cd.getName()+"' does not have a method newBuilder");
            }
        } else {
            newBuilderMethod=null;
        }
    }

    void setEnumValues(BiMap<String,Short> enumValues) {
        this.enumValues=enumValues;
    }
    
    /*
     * enums are deserialized as shorts 
     * (it is converted to the actual type in the {@link TableDefinition#deserialize(byte[], byte[])})
     */
    Object deserialize(java.io.DataInput stream) throws IOException {
        switch(type.val) {
        case BOOLEAN:
            return stream.readBoolean();
        case BYTE:
            return stream.readByte();
        case SHORT:
            return stream.readShort();
        case INT:
            return stream.readInt();
        case DOUBLE:
            return stream.readDouble();
        case TIMESTAMP:
            return stream.readLong();
        case STRING:
            return stream.readUTF();
        case BINARY:
            int length=stream.readInt();
            if(length>maxBinaryLength) { 
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): "+length+">"+maxBinaryLength);
                return null;
            }
            byte[] bp=new byte[length];
            stream.readFully(bp);
            return bp;
        case PROTOBUF:
            length=stream.readInt();
            if(length>maxBinaryLength) { 
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): "+length+">"+maxBinaryLength);
                return null;
            }
            bp=new byte[length];
            stream.readFully(bp);
            return readProtobufMessage(bp);
        case ENUM:
            short x=stream.readShort();
            return enumValues.inverse().get(x);
        case LIST:
        case TUPLE:
            //TODO
        }
        throw new IllegalStateException();
    }

    
    private MessageLite readProtobufMessage(byte[] bp) {
        if(newBuilderMethod==null) {
         
        }
        try {
            Builder b=(Builder) newBuilderMethod.invoke(null);
            b.mergeFrom(bp);
            return b.build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @throws IOException
     */
    public void serialize(DataOutput stream, Object v) throws IOException {
        switch(type.val) {
        case BOOLEAN:
            stream.writeBoolean((Boolean)v);
            break;
        case BYTE:
            stream.writeByte((Byte)v);
            break;
        case SHORT:
            stream.writeShort((Short)v);
            break;
        case INT:
            stream.writeInt((Integer)v);
            break;
        case DOUBLE:
            stream.writeDouble((Double)v);
            break;
        case TIMESTAMP:
            stream.writeLong((Long)v);
            break;
        case STRING:
            stream.writeUTF((String)v);
            break;
        case BINARY:
            byte[]va=(byte[])v;
            stream.writeInt(va.length);
            stream.write(va);
            break;
        case PROTOBUF:
            MessageLite m=(MessageLite)v;
            byte[] b=m.toByteArray();
            stream.writeInt(b.length);
            stream.write(b);
            break;
        case ENUM:
            Short v1;
            if((enumValues==null) || (v1=enumValues.get(v))==null) {
                tblDef.addEnumValue(this, (String)v);
                serialize(stream, v);
                return;
            }
            stream.writeShort(v1);
            break;
        case LIST:
        case TUPLE:
            //TODO
        }
        //TODO this is an exception
    }
    
    public byte[] getByteArray(Object v) {
        ByteArrayDataOutput bado=ByteStreams.newDataOutput();
        try {
            serialize(bado, v);
        } catch (IOException e) {
            throw new RuntimeException("cannot serialize in memory?", e);
        }
        return bado.toByteArray();
    }
   
    public Object fromByteArray(byte[] b) throws IOException {
        ByteArrayDataInput badi=ByteStreams.newDataInput(b);
        return deserialize(badi);
    }
    
    DataType getType() {
        return cd.getType();
    }
    
    String getColumnName() {
        return cd.getName();
    }
    
    @Override
    public String toString() {
        return String.format("ColumnSerializer(%s %s)", cd.getName(), type);
    }

}
