package org.yamcs.yarch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.DataType._type;

import com.google.common.collect.BiMap;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.CodedOutputStream.OutOfSpaceException;
import com.google.protobuf.MessageLite.Builder;

public class ColumnSerializerFactory {
    static YConfiguration config;
    static int maxBinaryLength = 1048576;
    static Logger log = LoggerFactory.getLogger(ColumnSerializer.class.getName());

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
        config = YConfiguration.getConfiguration("yamcs");
        if (config.containsKey("maxBinaryLength")) {
            maxBinaryLength = config.getInt("maxBinaryLength");
        }
    }

    public static ColumnSerializer<?> getColumnSerializer(TableDefinition tblDef, ColumnDefinition cd) {
        DataType type = cd.getType();
        if (type.val == _type.ENUM) {
            return new EnumColumnSerializer(tblDef, cd);
        } else if (type.val == _type.PROTOBUF) {
            return getProtobufSerializer(cd);
        } else {
            return getBasicColumnSerializer(cd.getType());
        }
    }

    public static ColumnSerializer<?> getColumnSerializer(ColumnDefinition cd) {
        DataType type = cd.getType();
        if (type.val == _type.ENUM) {
            return STRING_CS;
        } else if (type.val == _type.PROTOBUF) {
            return getProtobufSerializer(cd);
        } else {
            return getBasicColumnSerializer(cd.getType());
        }
    }

    /**
     * returns a column serializer for basic types
     * 
     * @param type
     * @return
     */
    @SuppressWarnings("incomplete-switch")
    public static <T>ColumnSerializer<T> getBasicColumnSerializer(DataType type) {
        switch (type.val) {
        case BOOLEAN:
            return (ColumnSerializer<T>) BOOLEAN_CS;
        case BYTE:
            return (ColumnSerializer<T>)BYTE_CS;
        case SHORT:
            return (ColumnSerializer<T>)SHORT_CS;
        case INT:
            return (ColumnSerializer<T>)INT_CS;
        case DOUBLE:
            return (ColumnSerializer<T>)DOUBLE_CS;
        case TIMESTAMP:
        case LONG: // intentional fall through
            return (ColumnSerializer<T>)LONG_CS;
        case STRING:
            return (ColumnSerializer<T>)STRING_CS;
        case BINARY:
            return (ColumnSerializer<T>)BINARY_CS;
        case PARAMETER_VALUE:
            return (ColumnSerializer<T>)PARAMETER_VALUE_CS;
        case LIST:
        case TUPLE:
            // TODO
            throw new UnsupportedOperationException("List and Tuple not implemented");
        }
        throw new IllegalArgumentException("' " + type + " is not a basic type");
    }

    static public <T extends MessageLite> ColumnSerializer<T> getProtobufSerializer(ColumnDefinition cd) {
        String className = ((ProtobufDataType) cd.getType()).getClassName();

        synchronized (protoSerialziers) {
            ProtobufColumnSerializer pcs = protoSerialziers.get(className);
            if (pcs != null) {
                return (ColumnSerializer<T>) pcs;
            }
            Class<?> c;
            try {
                c = Class.forName(className);
                Method newBuilderMethod = c.getMethod("newBuilder");
                pcs = new ProtobufColumnSerializer(newBuilderMethod);
                protoSerialziers.put(className, pcs);
                return (ColumnSerializer<T>) pcs;
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(
                        "Cannot find class '" + className + "' required to deserialize column '" + cd.getName() + "'",
                        e);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Class '" + className + "' required to deserialize column '"
                        + cd.getName() + "' does not have a method newBuilder", e);
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

        @Override
        public byte[] toByteArray(T v) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(size)) {
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
        public Boolean deserialize(ByteBuffer buf, ColumnDefinition cd) {
            return buf.get() != 0;
        }

        @Override
        public void serialize(DataOutputStream stream, Boolean v) throws IOException {
            stream.writeBoolean(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Boolean v) {
            byteBuf.put(v ? (byte) 1 : (byte) 0);
        }

        @Override
        public byte[] toByteArray(Boolean v) {
            boolean b = (Boolean) v;
            return new byte[] { (byte) (b ? 1 : 0) };
        }

        @Override
        public Boolean fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return b[0] != 0;
        }
    }

    static class ByteColumnSerializer implements ColumnSerializer<Byte> {
        @Override
        public Byte deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readByte();
        }

        @Override
        public Byte deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.get();
        }

        @Override
        public void serialize(DataOutputStream stream, Byte v) throws IOException {
            stream.writeByte((Byte) v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Byte v) {
            byteBuf.put(v);
        }

        @Override
        public byte[] toByteArray(Byte v) {
            return new byte[] { v };
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
        public Short deserialize(ByteBuffer buf, ColumnDefinition cd) {
            return buf.getShort();
        }

        @Override
        public void serialize(DataOutputStream stream, Short v) throws IOException {
            stream.writeShort((Short) v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Short v) {
            byteBuf.putShort((Short) v);
        }

        @Override
        public byte[] toByteArray(Short v) {
            short s = v;
            return new byte[] { (byte) ((s >> 8) & 0xFF), (byte) (s & 0xFF) };
        }

        @Override
        public Short fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return (short) (((b[0] & 0xFF) << 8) + (b[1] & 0xFF));
        }
    }

    static class IntegerColumnSerializer implements ColumnSerializer<Integer> {
        @Override
        public Integer deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readInt();
        }

        @Override
        public void serialize(DataOutputStream stream, Integer v) throws IOException {
            stream.writeInt((Integer) v);
        }

        @Override
        public Integer deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.getInt();
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Integer v) {
            byteBuf.putInt((Integer) v);
        }

        @Override
        public byte[] toByteArray(Integer v) {
            int x = v;
            return new byte[] { (byte) ((x >> 24) & 0xFF), (byte) ((x >> 16) & 0xFF), (byte) ((x >> 8) & 0xFF),
                    (byte) (x & 0xFF) };
        }

        @Override
        public Integer fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return (b[0] << 24) + (b[1] << 16) + (b[2] << 8) + b[3];
        }

    }

    static class DoubleColumnSerializer extends AbstractColumnSerializer<Double> {
        public DoubleColumnSerializer() {
            super(8);
        }

        @Override
        public Double deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readDouble();
        }

        @Override
        public Double deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.getDouble();
        }

        @Override
        public void serialize(DataOutputStream stream, Double v) throws IOException {
            stream.writeDouble(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Double v) {
            byteBuf.putDouble(v);
        }
    }

    static class LongColumnSerializer extends AbstractColumnSerializer<Long> {
        public LongColumnSerializer() {
            super(8);
        }

        @Override
        public Long deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readLong();
        }

        @Override
        public Long deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.getLong();
        }

        @Override
        public void serialize(DataOutputStream stream, Long v) throws IOException {
            stream.writeLong(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Long v) {
            byteBuf.putLong(v);
        }

        @Override
        public byte[] toByteArray(Long v) {
            return ByteArrayUtils.encodeLong(v);
        }

        @Override
        public Long fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return ByteArrayUtils.decodeLong(b, 0);
        }
    }

    static class StringColumnSerializer extends AbstractColumnSerializer<String> {
        public StringColumnSerializer() {
            super(32);
        }

        @Override
        public String deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return stream.readUTF();
        }

        @Override
        public String deserialize(ByteBuffer byteBuf, ColumnDefinition cd) throws IOException {
            int len = byteBuf.getShort();

            char[] ca = new char[len];

            int k = 0;

            for (int i = 0; i < len; i++) {
                int char2, char3;
                int c = byteBuf.get() & 0xFF;
                int c4 = c >> 4;
                if (c4 <= 7) {
                    ca[k++] = (char) c;
                } else if (c4 == 12 || c4 == 13) {
                    char2 = byteBuf.get() & 0xFF;
                    ca[k++] = (char) (((c & 0x1F) << 6) |
                            (char2 & 0x3F));
                } else if (c4 == 14) {
                    char2 = byteBuf.get() & 0xFF;
                    char3 = byteBuf.get() & 0xFF;
                    ca[k++] = (char) (((c & 0x0F) << 12) |
                            ((char2 & 0x3F) << 6) |
                            ((char3 & 0x3F) << 0));
                } else {
                    throw new IOException("invalid UTF8 string at byte" + i);
                }
            }
            // The number of chars produced may be less than utflen
            return new String(ca, 0, k);
        }

        @Override
        public void serialize(DataOutputStream stream, String v) throws IOException {
            stream.writeUTF(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, String v) {
            int strlen = v.length();
            int len = 0;
            int c;

            int pos = byteBuf.position();
            byteBuf.putShort((short) 0);

            for (int i = 0; i < strlen; i++) {
                c = v.charAt(i);
                if ((c > 0) && (c < 0x80)) {
                    byteBuf.put((byte) c);
                    len++;
                } else if (c < 0x0800) {// this cover also the null characters (c=0)
                    byteBuf.put((byte) (0xC0 | ((c >> 6) & 0x1F)));
                    byteBuf.put((byte) (0x80 | ((c >> 0) & 0x3F)));
                    len += 2;
                } else {
                    byteBuf.put((byte) (0xE0 | ((c >> 12) & 0x0F)));
                    byteBuf.put((byte) (0x80 | ((c >> 6) & 0x3F)));
                    byteBuf.put((byte) (0x80 | ((c >> 0) & 0x3F)));
                    len += 3;
                }
            }

            if (len > 0xFFFF) {
                throw new BufferOverflowException();
            }

            byteBuf.putShort(pos, (short) len);
        }
    }

    static class BinaryColumnSerializer implements ColumnSerializer<byte[]> {
        @Override
        public byte[] deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            int length = stream.readInt();
            if (length > maxBinaryLength) {
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): ?>?", length,
                        maxBinaryLength);
                return null;
            }
            byte[] bp = new byte[length];
            stream.readFully(bp);
            return bp;
        }

        @Override
        public byte[] deserialize(ByteBuffer byteBuf, ColumnDefinition cd) throws IOException {
            int length = byteBuf.getInt();
            if (length > maxBinaryLength) {
                throw new IOException("binary length " + length + " greater than maxBinaryLenght " + maxBinaryLength
                        + " (is the endianess wrong?)");
            }
            byte[] bp = new byte[length];
            byteBuf.get(bp);
            return bp;
        }

        @Override
        public void serialize(DataOutputStream stream, byte[] v) throws IOException {
            stream.writeInt(v.length);
            stream.write(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, byte[] v) {
            byteBuf.putInt(v.length);
            byteBuf.put(v);
        }

        @Override
        public byte[] toByteArray(byte[] v) {
            byte[] r = new byte[4 + v.length];
            ByteArrayUtils.encodeInt(v.length, r, 0);
            System.arraycopy(v, 0, r, 4, v.length);
            return r;
        }

        @Override
        public byte[] fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            byte[] r = new byte[b.length - 4];
            System.arraycopy(b, 4, r, 0, r.length);
            return r;
        }

    }

    static class ProtobufColumnSerializer extends AbstractColumnSerializer<MessageLite> {
        // for columns of type PROTOBUF
        private final Method newBuilderMethod;

        public ProtobufColumnSerializer(Method newBuilderMethod) {
            super(32);
            this.newBuilderMethod = newBuilderMethod;
        }

        @Override
        public MessageLite deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            int length = stream.readInt();
            if (length > maxBinaryLength) {
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): ?>?", length,
                        maxBinaryLength);
                throw new IOException("binary length greater than maxBinaryLength");
            }
            byte[] bp = new byte[length];
            stream.readFully(bp);
            return readProtobufMessage(bp);
        }

        @Override
        public MessageLite deserialize(ByteBuffer byteBuf, ColumnDefinition cd) throws IOException {
            int length = byteBuf.getInt();
            if (length > maxBinaryLength) {
                throw new IOException("binary length " + length + " greater than maxBinaryLenght " + maxBinaryLength);
            }
            Builder b;
            try {
                b = (Builder) newBuilderMethod.invoke(null);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
            int limit = byteBuf.limit();
            byteBuf.limit(byteBuf.position() + length);
            b.mergeFrom(CodedInputStream.newInstance(byteBuf));
            byteBuf.limit(limit);
            byteBuf.position(byteBuf.position() + length);

            return b.build();
        }

        @Override
        public void serialize(DataOutputStream stream, MessageLite v) throws IOException {
            byte[] b = v.toByteArray();
            stream.writeInt(b.length);
            stream.write(b);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, MessageLite v) {
            try {
                int position = byteBuf.position();
                byteBuf.putInt(0);
                CodedOutputStream cos = CodedOutputStream.newInstance(byteBuf);
                v.writeTo(cos);
                int size = cos.getTotalBytesWritten();
                byteBuf.position(position+size+4);
                byteBuf.putInt(position, size);
            } catch (IOException e) {
                if (e instanceof OutOfSpaceException) {
                    throw new BufferOverflowException();
                } else {
                    throw new UncheckedIOException(e);
                }
            }
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

    static class EnumColumnSerializer extends AbstractColumnSerializer<String> {
        private final TableDefinition tblDef;
        // for columns of type ENUM
        private volatile BiMap<String, Short> enumValues;
        private final String columnName;

        public EnumColumnSerializer(TableDefinition tblDef, ColumnDefinition cd) {
            super(2);
            this.tblDef = tblDef;
            this.columnName = cd.getName();
        }

        @Override
        public String deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            short x = stream.readShort();
            return enumValues.inverse().get(x);
        }

        @Override
        public String deserialize(ByteBuffer byteBuf, ColumnDefinition cd) throws IOException {
            short x = byteBuf.getShort();
            return enumValues.inverse().get(x);
        }

        @Override
        public void serialize(DataOutputStream stream, String v) throws IOException {
            Short v1;
            if ((enumValues == null) || (v1 = enumValues.get(v)) == null) {
                tblDef.addEnumValue(this, v);
                serialize(stream, v);
                return;
            }
            stream.writeShort(v1);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, String v) {
            Short v1;
            if ((enumValues == null) || (v1 = enumValues.get(v)) == null) {
                tblDef.addEnumValue(this, v);
                serialize(byteBuf, v);
                return;
            }
            byteBuf.putShort(v1);
        }

        void setEnumValues(BiMap<String, Short> enumValues) {
            this.enumValues = enumValues;
        }

        public String getColumnName() {
            return columnName;
        }

    }
}
