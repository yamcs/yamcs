package org.yamcs.yarch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.ColumnSerializerFactory.AbstractColumnSerializer;

public class ColumnSerializerV3 {
    static class ShortColumnSerializer implements ColumnSerializer<Short> {
        static short invertSign(short x) {
            return (short) (x ^ Short.MIN_VALUE);
        }

        @Override
        public Short deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return invertSign(stream.readShort());
        }

        @Override
        public Short deserialize(ByteBuffer buf, ColumnDefinition cd) {
            return invertSign(buf.getShort());
        }

        @Override
        public void serialize(DataOutputStream stream, Short v) throws IOException {
            stream.writeShort(invertSign((Short) v));
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Short v) {
            byteBuf.putShort(invertSign((Short) v));
        }

        @Override
        public byte[] toByteArray(Short v) {
            short s = invertSign(v);
            return new byte[] { (byte) ((s >> 8) & 0xFF), (byte) (s & 0xFF) };
        }

        @Override
        public Short fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return invertSign((short) (((b[0] & 0xFF) << 8) + (b[1] & 0xFF)));
        }
    }

    static class IntegerColumnSerializer implements ColumnSerializer<Integer> {
        static int invertSign(int x) {
            return x ^ Integer.MIN_VALUE;
        }
        
        @Override
        public Integer deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return invertSign(stream.readInt());
        }

        @Override
        public void serialize(DataOutputStream stream, Integer v) throws IOException {
            stream.writeInt(invertSign(v));
        }

        @Override
        public Integer deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return invertSign(byteBuf.getInt());
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Integer v) {
            byteBuf.putInt(invertSign(v));
        }

        @Override
        public byte[] toByteArray(Integer v) {
            return ByteArrayUtils.encodeInt(invertSign(v));
        }

        @Override
        public Integer fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return invertSign(ByteArrayUtils.decodeInt(b, 0));
        }

    }

    
    static class LongColumnSerializer extends AbstractColumnSerializer<Long> {
        static long invertSign(long x) {
            return x ^ Long.MIN_VALUE;
        }
        
        public LongColumnSerializer() {
            super(8);
        }

        @Override
        public Long deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return invertSign(stream.readLong());
        }

        @Override
        public Long deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return invertSign(byteBuf.getLong());
        }

        @Override
        public void serialize(DataOutputStream stream, Long v) throws IOException {
            stream.writeLong(invertSign(v));
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Long v) {
            byteBuf.putLong(invertSign(v));
        }

        @Override
        public byte[] toByteArray(Long v) {
            return ByteArrayUtils.encodeLong(invertSign(v));
        }

        @Override
        public Long fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            return invertSign(ByteArrayUtils.decodeLong(b, 0));
        }
    }

    static class DoubleColumnSerializer extends AbstractColumnSerializer<Double> {
        
        static long doubleToLong(double x) {
            long v = Double.doubleToLongBits(x);
            
            //for negative values, flips all the bits
            //for positive values, flips only the sign=first bit
            v ^= (v >> 63) | Long.MIN_VALUE;
            return v;
        }

        static double longToDouble(long x) {
            x ^= (~x >> 63) | Long.MIN_VALUE;
            return Double.longBitsToDouble(x);
        }

        
        public DoubleColumnSerializer() {
            super(8);
        }

        @Override
        public Double deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            return longToDouble(stream.readLong());
        }

        @Override
        public Double deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return longToDouble(byteBuf.getLong());
        }

        @Override
        public void serialize(DataOutputStream stream, Double v) throws IOException {
            stream.writeLong(doubleToLong(v));
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Double v) {
            byteBuf.putLong(doubleToLong(v));
        }
    }

    
    static class HresTimestampColumnSerializer extends AbstractColumnSerializer<Instant> {
        static long invertSign(long x) {
            return x ^ Long.MIN_VALUE;
        }
        
        public HresTimestampColumnSerializer() {
            super(12);
        }

        @Override
        public Instant deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
            long millis = invertSign(stream.readLong());
            int picos = stream.readInt();
            return Instant.get(millis, picos);
        }

        @Override
        public Instant deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            long millis = invertSign(byteBuf.getLong());
            int picos = byteBuf.getInt();
            return Instant.get(millis, picos);
        }

        @Override
        public void serialize(DataOutputStream stream, Instant v) throws IOException {
            stream.writeLong(invertSign(v.getMillis()));
            stream.writeInt(v.getPicos());
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Instant v) {
            byteBuf.putLong(invertSign(v.getMillis()));
            byteBuf.putInt(v.getPicos());
        }

        @Override
        public byte[] toByteArray(Instant v) {
            byte[] b = new byte[12];
            ByteArrayUtils.encodeLong(invertSign(v.getMillis()), b, 0);
            ByteArrayUtils.encodeInt(v.getPicos(), b, 8);
            return b;
        }

        @Override
        public Instant fromByteArray(byte[] b, ColumnDefinition cd) throws IOException {
            long millis = invertSign(ByteArrayUtils.decodeLong(b, 0));
            int picos = ByteArrayUtils.decodeInt(b, 8);
            return Instant.get(millis, picos);
        }
    }

}
