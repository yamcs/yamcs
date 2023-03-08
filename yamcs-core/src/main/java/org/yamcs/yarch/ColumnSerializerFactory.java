package org.yamcs.yarch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArray;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.yarch.DataType._type;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.CodedOutputStream.OutOfSpaceException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLite.Builder;

public class ColumnSerializerFactory {
    static YConfiguration config;
    static int maxBinaryLength = 1048576;
    static Logger log = LoggerFactory.getLogger(ColumnSerializer.class.getName());

    static final BooleanColumnSerializer BOOLEAN_CS = new BooleanColumnSerializer();
    static final ByteColumnSerializer BYTE_CS = new ByteColumnSerializer();
    static final ColumnSerializer<Short> SHORT_CS_V2 = new ColumnSerializerV2.ShortColumnSerializer();
    static final ColumnSerializer<Integer> INT_CS_V2 = new ColumnSerializerV2.IntegerColumnSerializer();
    static final ColumnSerializer<Long> LONG_CS_V2 = new ColumnSerializerV2.LongColumnSerializer();

    static final ColumnSerializer<Short> SHORT_CS_V3 = new ColumnSerializerV3.ShortColumnSerializer();
    static final ColumnSerializer<Integer> INT_CS_V3 = new ColumnSerializerV3.IntegerColumnSerializer();
    static final ColumnSerializer<Long> LONG_CS_V3 = new ColumnSerializerV3.LongColumnSerializer();

    static final ColumnSerializer<Double> DOUBLE_CS_V3 = new ColumnSerializerV3.DoubleColumnSerializer();
    static final ColumnSerializer<Double> DOUBLE_CS_V2 = new ColumnSerializerV2.DoubleColumnSerializer();

    static final SizePrefixedtringColumnSerializer STRING_CS_V2 = new SizePrefixedtringColumnSerializer();
    static final NullTerminatedStringColumnSerializer STRING_CS_V3 = new NullTerminatedStringColumnSerializer();

    static final BinaryColumnSerializer BINARY_CS = new BinaryColumnSerializer();

    static final ColumnSerializer<Instant> HRES_TIMESTAMP_CS_V2 = new ColumnSerializerV2.HresTimestampColumnSerializer();
    static final ColumnSerializer<Instant> HRES_TIMESTAMP_CS_V3 = new ColumnSerializerV3.HresTimestampColumnSerializer();
    static final ColumnSerializer<java.util.UUID> UUID_CS = new ColumnSerializerV3.UUIDColumnSerializer();

    static final ParameterValueColumnSerializer PARAMETER_VALUE_CS = new ParameterValueColumnSerializer();

    static Map<String, ProtobufColumnSerializer> protoSerialziers = new HashMap<>();

    static {
        config = YConfiguration.getConfiguration("yamcs").getConfigOrEmpty("archive");
        if (config.containsKey("maxBinaryLength")) {
            maxBinaryLength = config.getInt("maxBinaryLength");
        }
    }

    public static <T> ColumnSerializer<T> getColumnSerializer(TableDefinition tblDef, TableColumnDefinition cd) {
        return getColumnSerializer(tblDef, cd, cd.getType());
    }

    public static <T> ColumnSerializer<T> getColumnSerializer(TableDefinition tblDef, TableColumnDefinition cd,
            DataType type) {
        if (type.val == _type.ENUM) {
            return (ColumnSerializer<T>) new EnumColumnSerializer(tblDef, cd);
        } else if (type.val == _type.PROTOBUF) {
            return (ColumnSerializer<T>) getProtobufSerializer(cd);
        } else if (type.val == _type.ARRAY) {
            DataType elementType = ((ArrayDataType) type).getElementType();
            return (ColumnSerializer<T>) new ColumnSerializerV3.ArrayColumnSerializer(
                    getColumnSerializer(tblDef, cd, elementType));
        } else {
            if (tblDef.getFormatVersion() < 3) {
                return getBasicColumnSerializerV2(type);
            } else {
                return getBasicColumnSerializerV3(type);
            }
        }
    }

    /**
     * Returns the V2 serializers with the enumerations serialzied as strings (so they don't need a decoding table on
     * the other end)
     * 
     * @param cd
     * @return
     */
    public static ColumnSerializer<?> getColumnSerializerForReplication(ColumnDefinition cd) {
        return getColumnSerializerForReplication(cd.getType(), cd.getName());
    }

    public static ColumnSerializer<?> getColumnSerializerForReplication(DataType type, String colName) {
        if (type.val == _type.ENUM) {
            return STRING_CS_V2;
        } else if (type.val == _type.PROTOBUF) {
            return getProtobufSerializer((ProtobufDataType) type, colName);
        } else if (type.val == _type.ARRAY) {
            DataType elementType = ((ArrayDataType) type).getElementType();
            return (ColumnSerializer<?>) new ColumnSerializerV3.ArrayColumnSerializer(
                    getColumnSerializerForReplication(elementType, colName));
        } else if (type.val == _type.UUID) {
            return getBasicColumnSerializerV3(type);
        } else {
            // V2 is fine for replication as the serialized values are not used for sorting
            // should upgrade to V3 at some point but it will break compatibility with old replicated data
            return getBasicColumnSerializerV2(type);
        }
    }

    /**
     * returns a column serializer for basic types
     * 
     * @param type
     * @return
     */
    @SuppressWarnings({ "incomplete-switch", "unchecked" })
    public static <T> ColumnSerializer<T> getBasicColumnSerializerV3(DataType type) {
        switch (type.val) {
        case BOOLEAN:
            return (ColumnSerializer<T>) BOOLEAN_CS;
        case BYTE:
            return (ColumnSerializer<T>) BYTE_CS;
        case SHORT:
            return (ColumnSerializer<T>) SHORT_CS_V3;
        case INT:
            return (ColumnSerializer<T>) INT_CS_V3;
        case DOUBLE:
            return (ColumnSerializer<T>) DOUBLE_CS_V3;
        case TIMESTAMP:
        case LONG: // intentional fall through
            return (ColumnSerializer<T>) LONG_CS_V3;
        case STRING:
            return (ColumnSerializer<T>) STRING_CS_V3;
        case BINARY:
            return (ColumnSerializer<T>) BINARY_CS;
        case PARAMETER_VALUE:
            return (ColumnSerializer<T>) PARAMETER_VALUE_CS;
        case HRES_TIMESTAMP:
            return (ColumnSerializer<T>) HRES_TIMESTAMP_CS_V3;
        case UUID:
            return (ColumnSerializer<T>) UUID_CS;
        case TUPLE:
            // TODO
            throw new UnsupportedOperationException("Tuple not implemented");
        default:
            throw new IllegalArgumentException("' " + type + " is not a basic type");
        }
    }

    /**
     * returns a column serializer for basic types
     * 
     * @param type
     * @return
     */
    @SuppressWarnings({ "incomplete-switch", "unchecked" })
    public static <T> ColumnSerializer<T> getBasicColumnSerializerV2(DataType type) {
        switch (type.val) {
        case BOOLEAN:
            return (ColumnSerializer<T>) BOOLEAN_CS;
        case BYTE:
            return (ColumnSerializer<T>) BYTE_CS;
        case SHORT:
            return (ColumnSerializer<T>) SHORT_CS_V2;
        case INT:
            return (ColumnSerializer<T>) INT_CS_V2;
        case DOUBLE:
            return (ColumnSerializer<T>) DOUBLE_CS_V2;
        case TIMESTAMP:
        case LONG: // intentional fall through
            return (ColumnSerializer<T>) LONG_CS_V2;
        case STRING:
            return (ColumnSerializer<T>) STRING_CS_V2;
        case BINARY:
            return (ColumnSerializer<T>) BINARY_CS;
        case PARAMETER_VALUE:
            return (ColumnSerializer<T>) PARAMETER_VALUE_CS;
        case HRES_TIMESTAMP:
            return (ColumnSerializer<T>) HRES_TIMESTAMP_CS_V2;
        case ARRAY:
        case TUPLE:
            // TODO
            throw new UnsupportedOperationException("List and Tuple not implemented");
        default:
            throw new IllegalArgumentException("' " + type + " is not a basic type");
        }
    }

    @SuppressWarnings("unchecked")
    static public <T extends MessageLite> ColumnSerializer<T> getProtobufSerializer(ColumnDefinition cd) {
        return getProtobufSerializer((ProtobufDataType) cd.getType(), cd.getName());
    }

    @SuppressWarnings("unchecked")
    static public <T extends MessageLite> ColumnSerializer<T> getProtobufSerializer(ProtobufDataType dtype,
            String colName) {
        String className = dtype.getClassName();

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
                        "Cannot find class '" + className + "' required to deserialize column '" + colName + "'",
                        e);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Class '" + className + "' required to deserialize column '"
                        + colName + "' does not have a method newBuilder", e);
            }
        }
    }

    static class BooleanColumnSerializer implements ColumnSerializer<Boolean> {
        @Override
        public Boolean deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return byteArray.get() != 0;
        }

        @Override
        public Boolean deserialize(ByteBuffer buf, ColumnDefinition cd) {
            return buf.get() != 0;
        }

        @Override
        public void serialize(ByteArray ba, Boolean v) {
            ba.add(v ? (byte) 1 : (byte) 0);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Boolean v) {
            byteBuf.put(v ? (byte) 1 : (byte) 0);
        }

    }

    static class ByteColumnSerializer implements ColumnSerializer<Byte> {
        @Override
        public Byte deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return byteArray.get();
        }

        @Override
        public Byte deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.get();
        }

        @Override
        public void serialize(ByteArray ba, Byte v) {
            ba.add(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Byte v) {
            byteBuf.put(v);
        }
    }

    static class NullTerminatedStringColumnSerializer implements ColumnSerializer<String> {
        @Override
        public String deserialize(ByteArray byteArray, ColumnDefinition cd) {
            try {
                return byteArray.getNullTerminatedUTF();
            } catch (DecodingException e) {
                throw new DatabaseCorruptionException(e);
            }
        }

        @Override
        public String deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return decodeUTF(byteBuf, true);
        }

        @Override
        public void serialize(ByteArray byteArray, String v) {
            byteArray.addNullTerminatedUTF(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, String v) {
            encodeUTF(byteBuf, v);
            byteBuf.put((byte) 0);
        }
    }

    static class SizePrefixedtringColumnSerializer implements ColumnSerializer<String> {

        @Override
        public String deserialize(ByteArray byteArray, ColumnDefinition cd) {
            try {
                return byteArray.getSizePrefixedUTF();
            } catch (DecodingException e) {
                throw new DatabaseCorruptionException(e);
            }
        }

        @Override
        public String deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            int len = byteBuf.getShort();
            if (len > byteBuf.remaining()) {
                throw new BufferUnderflowException();
            }
            int oldlimit = byteBuf.limit();
            byteBuf.limit(byteBuf.position() + len);
            String s = decodeUTF(byteBuf, false);
            byteBuf.limit(oldlimit);
            return s;
        }

        @Override
        public void serialize(ByteArray byteArray, String v) {
            byteArray.addSizePrefixedUTF(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, String v) {
            int pos = byteBuf.position();
            byteBuf.putShort((short) 0);
            encodeUTF(byteBuf, v);
            byteBuf.putShort(pos, (short) (byteBuf.position() - pos - 2));
        }
    }

    static private void encodeUTF(ByteBuffer byteBuf, String v) {
        int strlen = v.length();
        int c;
        int len = 0;
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
    }

    static private String decodeUTF(ByteBuffer byteBuf, boolean nullTerminated) {
        char[] ca = new char[byteBuf.remaining()];
        int k = 0;

        while (byteBuf.hasRemaining()) {
            int c = byteBuf.get() & 0xFF;
            if (nullTerminated && c == 0) {
                break;
            }

            int char2, char3;
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
                throw new DatabaseCorruptionException("invalid UTF8 string at byte" + byteBuf.position());
            }
        }
        return new String(ca, 0, k);
    }

    static class BinaryColumnSerializer implements ColumnSerializer<byte[]> {
        @Override
        public byte[] deserialize(ByteArray byteArray, ColumnDefinition cd) {
            int length = byteArray.getInt();
            if (length > maxBinaryLength) {
                log.warn("binary length greater than maxBinaryLenght (is the endianess wrong?): ?>?", length,
                        maxBinaryLength);
                return null;
            }
            byte[] bp = new byte[length];
            byteArray.get(bp);
            return bp;
        }

        @Override
        public byte[] deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            int length = byteBuf.getInt();
            if (length > maxBinaryLength) {
                throw new YarchException("binary length " + length + " greater than maxBinaryLenght " + maxBinaryLength
                        + " (is the endianess wrong?)");
            }
            byte[] bp = new byte[length];
            byteBuf.get(bp);
            return bp;
        }

        @Override
        public void serialize(ByteArray byteArray, byte[] v) {
            byteArray.addInt(v.length);
            byteArray.add(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, byte[] v) {
            byteBuf.putInt(v.length);
            byteBuf.put(v);
        }
    }

    static class ProtobufColumnSerializer implements ColumnSerializer<MessageLite> {
        // for columns of type PROTOBUF
        private final Method newBuilderMethod;

        public ProtobufColumnSerializer(Method newBuilderMethod) {
            this.newBuilderMethod = newBuilderMethod;
        }

        @Override
        public MessageLite deserialize(ByteArray byteArray, ColumnDefinition cd) {
            try {
                Builder b = (Builder) newBuilderMethod.invoke(null);
                byteArray.getSizePrefixedProto(b);
                return b.build();
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public MessageLite deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            int length = byteBuf.getInt();
            if (length > maxBinaryLength) {
                throw new YarchException(
                        "binary length " + length + " greater than maxBinaryLenght " + maxBinaryLength);
            }
            Builder b;
            try {
                b = (Builder) newBuilderMethod.invoke(null);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
            int limit = byteBuf.limit();
            byteBuf.limit(byteBuf.position() + length);
            try {
                b.mergeFrom(CodedInputStream.newInstance(byteBuf));
            } catch (IOException e) {
                throw new YarchException(e);
            }
            byteBuf.limit(limit);
            byteBuf.position(byteBuf.position() + length);

            return b.build();
        }

        @Override
        public void serialize(ByteArray byteArray, MessageLite v) {
            byteArray.addSizePrefixedProto(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, MessageLite v) {
            try {
                int position = byteBuf.position();
                byteBuf.putInt(0);
                CodedOutputStream cos = CodedOutputStream.newInstance(byteBuf);
                v.writeTo(cos);
                int size = cos.getTotalBytesWritten();
                byteBuf.position(position + size + 4);
                byteBuf.putInt(position, size);
            } catch (IOException e) {
                if (e instanceof OutOfSpaceException) {
                    throw new BufferOverflowException();
                } else {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    static class EnumColumnSerializer implements ColumnSerializer<String> {
        private final TableDefinition tblDef;
        TableColumnDefinition colDef;
        String columnName;

        public EnumColumnSerializer(TableDefinition tblDef, TableColumnDefinition colDef) {
            this.tblDef = tblDef;
            this.columnName = colDef.getName();
            this.colDef = colDef;
        }

        String getValue(short x) {
            String v = colDef.getEnumValue(x);
            if (v == null) { // probably the column definition has changed
                colDef = tblDef.getColumnDefinition(columnName);
                v = colDef.getEnumValue(x);
            }
            return v;
        }

        short getIndex(String value) {
            Short idx = colDef.getEnumIndex(value);
            if (idx == null) {
                idx = tblDef.addAndGetEnumValue(columnName, value);
                colDef = tblDef.getColumnDefinition(columnName);
            }
            return idx;
        }

        @Override
        public String deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return getValue(byteArray.getShort());
        }

        @Override
        public String deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return getValue(byteBuf.getShort());
        }

        @Override
        public void serialize(ByteArray byteArray, String value) {
            byteArray.addShort(getIndex(value));
        }

        @Override
        public void serialize(ByteBuffer byteBuf, String value) {
            byteBuf.putShort(getIndex(value));
        }

        public String getColumnName() {
            return columnName;
        }
    }
}
