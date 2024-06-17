package org.yamcs.yarch;

import java.nio.ByteBuffer;

import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArray;
import org.yamcs.utils.ByteArrayUtils;

/**
 * serializers for table format version &#8804; 2 where the signed integers (and timestamps) are stored as they are and
 * do not sort properly when they are primary keys.
 *
 */
public class ColumnSerializerV2 {
    static class ShortColumnSerializer implements ColumnSerializer<Short> {
        @Override
        public Short deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return byteArray.getShort();
        }

        @Override
        public Short deserialize(ByteBuffer buf, ColumnDefinition cd) {
            return buf.getShort();
        }

        @Override
        public void serialize(ByteArray byteArray, Short v) {
            byteArray.addShort(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Short v) {
            byteBuf.putShort(v);
        }
    }

    static class IntegerColumnSerializer implements ColumnSerializer<Integer> {
        @Override
        public Integer deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return byteArray.getInt();
        }

        @Override
        public void serialize(ByteArray byteArray, Integer v) {
            byteArray.addInt(v);
        }

        @Override
        public Integer deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.getInt();
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Integer v) {
            byteBuf.putInt((Integer) v);
        }
    }

    static class LongColumnSerializer implements ColumnSerializer<Long> {
        @Override
        public Long deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return byteArray.getLong();
        }

        @Override
        public Long deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.getLong();
        }

        @Override
        public void serialize(ByteArray byteArray, Long v) {
            byteArray.addLong(v);
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
        public Long fromByteArray(byte[] b, ColumnDefinition cd) {
            return ByteArrayUtils.decodeLong(b, 0);
        }
    }


    static class HresTimestampColumnSerializer implements ColumnSerializer<Instant> {

        @Override
        public Instant deserialize(ByteArray byteArray, ColumnDefinition cd) {
            long millis = byteArray.getLong();
            int picos = byteArray.getInt();
            return Instant.get(millis, picos);
        }

        @Override
        public Instant deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            long millis = byteBuf.getLong();
            int picos = byteBuf.getInt();
            return Instant.get(millis, picos);
        }

        @Override
        public void serialize(ByteArray byteArray, Instant v) {
            byteArray.addLong(v.getMillis());
            byteArray.addInt(v.getPicos());
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
        public Instant fromByteArray(byte[] b, ColumnDefinition cd) {
            long millis = ByteArrayUtils.decodeLong(b, 0);
            int picos = ByteArrayUtils.decodeInt(b, 8);
            return Instant.get(millis, picos);
        }
    }
    static class DoubleColumnSerializer implements ColumnSerializer<Double> {

        @Override
        public Double deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return byteArray.getDouble();
        }

        @Override
        public Double deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return byteBuf.getDouble();
        }

        @Override
        public void serialize(ByteArray byteArray, Double v) {
            byteArray.addDouble(v);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Double v) {
            byteBuf.putDouble(v);
        }
    }

}
