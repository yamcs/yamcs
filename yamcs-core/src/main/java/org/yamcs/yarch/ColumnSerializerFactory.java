package org.yamcs.yarch;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

import com.google.common.collect.BiMap;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLite.Builder;

public class ColumnSerializerFactory {
    static YConfiguration config;
    static int maxBinaryLength=1048576;
    static Logger log=LoggerFactory.getLogger(ColumnSerializer.class.getName());
    
    static BooleanColumnSerializer BOOLEAN_CS = new BooleanColumnSerializer();
    static ByteColumnSerializer BYTE_CS = new ByteColumnSerializer();
    static ShortColumnSerializer SHORT_CS = new ShortColumnSerializer();
    static IntegerColumnSerializer INT_CS = new IntegerColumnSerializer();
    static LongColumnSerializer LONG_CS = new LongColumnSerializer();
    static DoubleColumnSerializer DOUBLE_CS = new DoubleColumnSerializer();
    static StringColumnSerializer STRING_CS = new StringColumnSerializer();
    static BinaryColumnSerializer BINARY_CS = new BinaryColumnSerializer();

    static Map<String, ProtobufColumnSerializer> protoSerialziers = new HashMap<>(); 
    
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

    static ColumnSerializer<?> getColumnSerializer(TableDefinition tblDef, ColumnDefinition cd) {
        DataType type = cd.getType();
        switch(type.val) {
        case BOOLEAN:
            return BOOLEAN_CS;
        case BYTE:
            return BYTE_CS;
        case SHORT:
            return SHORT_CS;
        case INT:
            return INT_CS;
        case DOUBLE:
            return DOUBLE_CS;
        case TIMESTAMP:
            return LONG_CS;
        case STRING:
            return STRING_CS;
        case BINARY:
            return BINARY_CS;
        case PROTOBUF:
            return getProSerializer(tblDef, cd);
        case ENUM:
            return new EnumColumnSerializer(tblDef, cd);

        case LIST:
        case TUPLE:
            //TODO
            throw new RuntimeException("List and Tuple not implemented");
        }
        throw new IllegalStateException();
    }
    
    
    
  static private ColumnSerializer<?> getProSerializer(TableDefinition tblDef,  ColumnDefinition cd) {
        String className = cd.getType().getClassName();
       
        synchronized(protoSerialziers) {
            ProtobufColumnSerializer pcs = protoSerialziers.get(className);
            if(pcs!=null) return pcs;
            Class<?> c;
            try {
                c = Class.forName(className);
                Method newBuilderMethod = c.getMethod("newBuilder");
                pcs = new ProtobufColumnSerializer(newBuilderMethod);
                protoSerialziers.put(className, pcs);
                return pcs;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot find class '"+className+"' required to deserialize column '"+cd.getName()+"'");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Class '"+className+"' required to deserialize column '"+cd.getName()+"' does not have a method newBuilder");
            }
        }
    }



    static abstract class AbstractColumnSerializer<T> implements ColumnSerializer<T> {
        int size;
        public AbstractColumnSerializer(int size) {
            this.size = size;
        }
        @Override
        public T fromByteArray(byte[] b) throws IOException {
            ByteArrayDataInput badi=ByteStreams.newDataInput(b);
            return deserialize(badi);
        }
        
        public byte[] getByteArray(T v) {
            ByteArrayDataOutput bado = ByteStreams.newDataOutput(size);
            try {
                serialize(bado, v);
            } catch (IOException e) {
                throw new RuntimeException("cannot serialize in memory?", e);
            }
            return bado.toByteArray();
        }
    }
    static class BooleanColumnSerializer implements ColumnSerializer<Boolean> {
        @Override
        public Boolean deserialize(DataInput stream) throws IOException {
            return stream.readBoolean();
        }

        @Override
        public void serialize(DataOutput stream, Boolean v) throws IOException {
            stream.writeBoolean((Boolean)v);
        }

        @Override
        public byte[] getByteArray(Boolean v) {
            boolean b = (Boolean)v;
            return new byte[]{(byte)(b?1:0)};
        }

        @Override
        public Boolean fromByteArray(byte[] b) throws IOException {
            return b[0]==1;
        }
    }

    static   class ByteColumnSerializer implements ColumnSerializer<Byte> {
        @Override
        public Byte deserialize(DataInput stream) throws IOException {
            return stream.readByte();
        }

        @Override
        public void serialize(DataOutput stream, Byte v) throws IOException {
            stream.writeByte((Byte)v);
        }

        @Override
        public byte[] getByteArray(Byte v) {
            return new byte[]{v};
        }

        @Override
        public Byte fromByteArray(byte[] b) throws IOException {
            return b[0];
        }
    }

    static class ShortColumnSerializer implements ColumnSerializer<Short> {
        @Override
        public Short deserialize(DataInput stream) throws IOException {
            return stream.readShort();
        }

        @Override
        public void serialize(DataOutput stream, Short v) throws IOException {
            stream.writeShort((Short)v);
        }

        @Override
        public byte[] getByteArray(Short v) {
            short s = v;
            return new byte[] { (byte)((s>>8)&0xFF), (byte) (s&0xFF)};
        }

        @Override
        public Short fromByteArray(byte[] b) throws IOException {
            return (short)((b[0]<<8) + b[1]);
        }
    }

    static  class IntegerColumnSerializer implements ColumnSerializer<Integer> {
        @Override
        public Integer deserialize(DataInput stream) throws IOException {
            return stream.readInt();
        }

        @Override
        public void serialize(DataOutput stream, Integer v) throws IOException {
            stream.writeInt((Integer)v);
        }

        @Override
        public byte[] getByteArray(Integer v) {
            int x = v;
            return new byte[] { (byte)((x>>24)&0xFF),  (byte)((x>>16)&0xFF),  (byte)((x>>8)&0xFF), (byte) (x&0xFF)};
        }

        @Override
        public Integer fromByteArray(byte[] b) throws IOException {
            return  (b[0]<<24) + (b[1]<<16) + (b[2]<<8) +b[3];
        }
    }
    
    static  class DoubleColumnSerializer extends AbstractColumnSerializer<Double> {
        public DoubleColumnSerializer() {
            super(8);
        }

        @Override
        public Double deserialize(DataInput stream) throws IOException {
            return stream.readDouble();
        }

        @Override
        public void serialize(DataOutput stream, Double v) throws IOException {
            stream.writeDouble(v);
        }
    }
    static  class LongColumnSerializer extends AbstractColumnSerializer<Long> {
        public LongColumnSerializer() {
            super(8);
        }

        @Override
        public Long deserialize(DataInput stream) throws IOException {
            return stream.readLong();
        }

        @Override
        public void serialize(DataOutput stream, Long v) throws IOException {
            stream.writeLong(v);
        }
    }

    static  class StringColumnSerializer extends AbstractColumnSerializer<String> {
        public StringColumnSerializer() {
            super(32);
        }

        @Override
        public String deserialize(DataInput stream) throws IOException {
            return stream.readUTF();
        }

        @Override
        public void serialize(DataOutput stream, String v) throws IOException {
            stream.writeUTF(v);
        }
    }

    static class BinaryColumnSerializer implements ColumnSerializer<byte[]> {
        @Override
        public byte[] deserialize(DataInput stream) throws IOException {
            int length=stream.readInt();
            if(length>maxBinaryLength) { 
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): "+length+">"+maxBinaryLength);
                return null;
            }
            byte[] bp = new byte[length];
            stream.readFully(bp);
            return bp;
        }

        @Override
        public void serialize(DataOutput stream, byte[] v) throws IOException {
            byte[]va=(byte[])v;
            stream.writeInt(va.length);
            stream.write(va);
        }

        @Override
        public byte[] getByteArray(byte[] v) {
            return v;
        }

        @Override
        public byte[] fromByteArray(byte[] b) throws IOException {
            return b;
        }
    }

    static    class ProtobufColumnSerializer extends AbstractColumnSerializer<MessageLite> {
        //for columns of type PROTOBUF
        private final Method newBuilderMethod; 
        public ProtobufColumnSerializer(Method newBuilderMethod) {
            super(32);
            this.newBuilderMethod = newBuilderMethod;
        }
        @Override
        public MessageLite deserialize(DataInput stream) throws IOException {
            int length=stream.readInt();
            if(length>maxBinaryLength) { 
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): "+length+">"+maxBinaryLength);
                return null;
            }
            byte[] bp = new byte[length];
            stream.readFully(bp);
            return readProtobufMessage(bp);
        }

        @Override
        public void serialize(DataOutput stream, MessageLite v)  throws IOException {
            byte[] b = v.toByteArray();
            stream.writeInt(b.length);
            stream.write(b);
        }

       

        private MessageLite readProtobufMessage(byte[] bp) {
            try {
                Builder b=(Builder) newBuilderMethod.invoke(null);
                b.mergeFrom(bp);
                return b.build();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    static  class EnumColumnSerializer extends AbstractColumnSerializer<String> {
        private final TableDefinition tblDef;
        //for columns of type ENUM
        private volatile BiMap<String,Short> enumValues;
        private final String columnName; 
        
        public EnumColumnSerializer(TableDefinition tblDef, ColumnDefinition cd) {
            super(2);
            this.tblDef = tblDef;
            this.columnName = cd.getName();
        }

       
        
        @Override
        public String deserialize(DataInput stream) throws IOException {
            short x=stream.readShort();
            return enumValues.inverse().get(x);
        }
        @Override
        public void serialize(DataOutput stream, String v)  throws IOException {
            Short v1;
            if((enumValues==null) || (v1=enumValues.get(v))==null) {
                tblDef.addEnumValue(this, v);
                serialize(stream, v);
                return;
            }
            stream.writeShort(v1);
        }

        void setEnumValues(BiMap<String,Short> enumValues) {
            this.enumValues=enumValues;
        }



        public String getColumnName() {
            return columnName;
        }
    }
}
