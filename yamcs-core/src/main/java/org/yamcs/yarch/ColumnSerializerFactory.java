package org.yamcs.yarch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.DataType._type;

import com.google.common.collect.BiMap;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLite.Builder;

public class ColumnSerializerFactory {
    static YConfiguration config;
    static int maxBinaryLength=1048576;
    static Logger log=LoggerFactory.getLogger(ColumnSerializer.class.getName());
    
    static final BooleanColumnSerializer BOOLEAN_CS = new BooleanColumnSerializer();
    static final ByteColumnSerializer BYTE_CS = new ByteColumnSerializer();
    static final ShortColumnSerializer SHORT_CS = new ShortColumnSerializer();
    static final IntegerColumnSerializer INT_CS = new IntegerColumnSerializer();
    static final LongColumnSerializer LONG_CS = new LongColumnSerializer();
    static final DoubleColumnSerializer DOUBLE_CS = new DoubleColumnSerializer();
    static final StringColumnSerializer STRING_CS = new StringColumnSerializer();
    static final BinaryColumnSerializer BINARY_CS = new BinaryColumnSerializer();
    static final ParameterValueColumnSerializer PARAMETER_VALUE_CS = new ParameterValueColumnSerializer();

    static Map<String, ProtobufColumnSerializer> protoSerialziers = new HashMap<>(); 
    
    static {
        config=YConfiguration.getConfiguration("yamcs");
        if(config.containsKey("maxBinaryLength")) {
            maxBinaryLength=config.getInt("maxBinaryLength");
        }
    } 

    public static ColumnSerializer<?> getColumnSerializer(TableDefinition tblDef, ColumnDefinition cd) {
        DataType type = cd.getType();
        if(type.val==_type.ENUM) {
            return new EnumColumnSerializer(tblDef, cd);
        } else if(type.val==_type.PROTOBUF) {
            return getProtobufSerializer(cd);
        } else {
            return getBasicColumnSerializer(cd.getType());
        }
    }
    
    /**
     * returns a column serializer for basic types
     * @param type
     * @return
     */
    @SuppressWarnings("incomplete-switch")
    public static ColumnSerializer<?> getBasicColumnSerializer(DataType type) {
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
        case PARAMETER_VALUE:
            return PARAMETER_VALUE_CS;
        case LIST:
        case TUPLE:
            //TODO
            throw new UnsupportedOperationException("List and Tuple not implemented");
        }
        throw new IllegalArgumentException("' "+type+" is not a basic type");
    }
    
    static public ColumnSerializer<?> getProtobufSerializer(ColumnDefinition cd) {
        String className = ((ProtobufDataType)cd.getType()).getClassName();
       
        synchronized(protoSerialziers) {
            ProtobufColumnSerializer pcs = protoSerialziers.get(className);
            if(pcs!=null) {
                return pcs;
            }
            Class<?> c;
            try {
                c = Class.forName(className);
                Method newBuilderMethod = c.getMethod("newBuilder");
                pcs = new ProtobufColumnSerializer(newBuilderMethod);
                protoSerialziers.put(className, pcs);
                return pcs;
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Cannot find class '"+className+"' required to deserialize column '"+cd.getName()+"'", e);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Class '"+className+"' required to deserialize column '"+cd.getName()+"' does not have a method newBuilder", e);
            }
        }
    }



    static abstract class AbstractColumnSerializer<T> implements ColumnSerializer<T> {
        int size;
        public AbstractColumnSerializer(int size) {
            this.size = size;
        }
        @Override
        public T fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            DataInputStream dos = new DataInputStream(new ByteArrayInputStream(b));
            return deserialize(dos, cd);
        }
        
        public byte[] toByteArray(T v) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(size)){
                DataOutputStream dos = new DataOutputStream(baos);
                serialize(dos, v);
                return baos.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException("cannot serialize in memory?", e);
            }
           
        }
    }
    static class BooleanColumnSerializer implements ColumnSerializer<Boolean> {
        @Override
        public Boolean deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readBoolean();
        }

        @Override
        public void serialize(DataOutputStream stream, Boolean v) throws IOException {
            stream.writeBoolean((Boolean)v);
        }

        @Override
        public byte[] toByteArray(Boolean v) {
            boolean b = (Boolean)v;
            return new byte[]{(byte)(b?1:0)};
        }

        @Override
        public Boolean fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return b[0]==1;
        }
    }

    static   class ByteColumnSerializer implements ColumnSerializer<Byte> {
        @Override
        public Byte deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readByte();
        }

        @Override
        public void serialize(DataOutputStream stream, Byte v) throws IOException {
            stream.writeByte((Byte)v);
        }

        @Override
        public byte[] toByteArray(Byte v) {
            return new byte[]{v};
        }

        @Override
        public Byte fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return b[0];
        }
    }

    static class ShortColumnSerializer implements ColumnSerializer<Short> {
        @Override
        public Short deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readShort();
        }

        @Override
        public void serialize(DataOutputStream stream, Short v) throws IOException {
            stream.writeShort((Short)v);
        }

        @Override
        public byte[] toByteArray(Short v) {
            short s = v;
            return new byte[] { (byte)((s>>8)&0xFF), (byte) (s&0xFF)};
        }

        @Override
        public Short fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return (short)(((b[0]&0xFF)<<8) + (b[1]&0xFF));
        }
    }

    static  class IntegerColumnSerializer implements ColumnSerializer<Integer> {
        @Override
        public Integer deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readInt();
        }

        @Override
        public void serialize(DataOutputStream stream, Integer v) throws IOException {
            stream.writeInt((Integer)v);
        }

        @Override
        public byte[] toByteArray(Integer v) {
            int x = v;
            return new byte[] { (byte)((x>>24)&0xFF),  (byte)((x>>16)&0xFF),  (byte)((x>>8)&0xFF), (byte) (x&0xFF)};
        }

        @Override
        public Integer fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return  (b[0]<<24) + (b[1]<<16) + (b[2]<<8) +b[3];
        }
    }
    
    static  class DoubleColumnSerializer extends AbstractColumnSerializer<Double> {
        public DoubleColumnSerializer() {
            super(8);
        }

        @Override
        public Double deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readDouble();
        }

        @Override
        public void serialize(DataOutputStream stream, Double v) throws IOException {
            stream.writeDouble(v);
        }
    }
    static  class LongColumnSerializer extends AbstractColumnSerializer<Long> {
        public LongColumnSerializer() {
            super(8);
        }

        @Override
        public Long deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readLong();
        }

        @Override
        public void serialize(DataOutputStream stream, Long v) throws IOException {
            stream.writeLong(v);
        }
    }

    static  class StringColumnSerializer extends AbstractColumnSerializer<String> {
        public StringColumnSerializer() {
            super(32);
        }

        @Override
        public String deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readUTF();
        }

        @Override
        public void serialize(DataOutputStream stream, String v) throws IOException {
            stream.writeUTF(v);
        }
    }

    static class BinaryColumnSerializer implements ColumnSerializer<byte[]> {
        @Override
        public byte[] deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            int length=stream.readInt();
            if(length>maxBinaryLength) { 
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): ?>?", length, maxBinaryLength);
                return null;
            }
            byte[] bp = new byte[length];
            stream.readFully(bp);
            return bp;
        }

        @Override
        public void serialize(DataOutputStream stream, byte[] v) throws IOException {
            stream.writeInt(v.length);
            stream.write(v);
        }

        @Override
        public byte[] toByteArray(byte[] v) {
            byte[] r = new byte[4+v.length];
            ByteArrayUtils.encodeInt(v.length, r, 0);
            System.arraycopy(v, 0, r, 4, v.length);
            return r;
        }

        @Override
        public byte[] fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            byte[] r = new byte[b.length-4];
            System.arraycopy(b, 4, r, 0, r.length);
            return r;
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
        public MessageLite deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            int length = stream.readInt();
            if(length>maxBinaryLength) { 
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): ?>?", length, maxBinaryLength);
                throw new IOException("binary length greater than maxBinaryLength");
            }
            byte[] bp = new byte[length];
            stream.readFully(bp);
            return readProtobufMessage(bp);
        }

        @Override
        public void serialize(DataOutputStream stream, MessageLite v)  throws IOException {
            byte[] b = v.toByteArray();
            stream.writeInt(b.length);
            stream.write(b);
        }

       

        private MessageLite readProtobufMessage(byte[] bp) throws InvalidProtocolBufferException {
            try {
                Builder b = (Builder) newBuilderMethod.invoke(null);
                b.mergeFrom(bp);
                return b.build();
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
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
        public String deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            short x=stream.readShort();
            return enumValues.inverse().get(x);
        }
        @Override
        public void serialize(DataOutputStream stream, String v)  throws IOException {
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
