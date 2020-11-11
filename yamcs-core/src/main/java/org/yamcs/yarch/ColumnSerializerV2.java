package org.yamcs.yarch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.ColumnSerializerFactory.AbstractColumnSerializer;

/**
 * serializers for table format version <2 where the signed integers (and timestamps) are stored as they are and do not
 * sort properly when they are primary keys.
 *
 */
public class ColumnSerializerV2 {
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
            return ByteArrayUtils.encodeInt(x);
        }

        @Override
        public Integer fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return ByteArrayUtils.decodeInt(b, 0);
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


    static class HresTimestampColumnSerializer extends AbstractColumnSerializer<Instant> {
        public HresTimestampColumnSerializer() {
            super(12);
        }

        @Override
        public Instant deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            long millis = stream.readLong();
            int picos = stream.readInt();
            return Instant.get(millis, picos);
        }

        @Override
        public Instant deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            long millis = byteBuf.getLong();
            int picos = byteBuf.getInt();
            return Instant.get(millis, picos);
        }

        @Override
        public void serialize(DataOutputStream stream, Instant v) throws IOException {
            stream.writeLong(v.getMillis());
            stream.writeInt(v.getPicos());
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Instant v) {
            byteBuf.putLong(v.getMillis());
            byteBuf.putInt(v.getPicos());
        }

        @Override
        public byte[] toByteArray(Instant v) {
            byte[] b= new byte[12];
            ByteArrayUtils.encodeLong(v.getMillis(), b, 0);
            ByteArrayUtils.encodeInt(v.getPicos(), b, 8);
            return b;
        }

        @Override
        public Instant fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            long millis = ByteArrayUtils.decodeLong(b, 0);
            int picos = ByteArrayUtils.decodeInt(b, 8);
            return Instant.get(millis, picos);
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

}
