package org.yamcs.yarch;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArray;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;

import static org.yamcs.yarch.ColumnSerializerFactory.*;

public class ColumnSerializerV3 {
    static short invertSign(short x) {
        return (short) (x ^ Short.MIN_VALUE);
    }
    static int invertSign(int x) {
        return x ^ Integer.MIN_VALUE;
    }
    static long invertSign(long x) {
        return x ^ Long.MIN_VALUE;
    } 

    static class ShortColumnSerializer implements ColumnSerializer<Short> {
        @Override
        public Short deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return invertSign(byteArray.getShort());
        }

        @Override
        public Short deserialize(ByteBuffer buf, ColumnDefinition cd) {
            return invertSign(buf.getShort());
        }

        @Override
        public void serialize(ByteArray byteArray, Short v) {
            byteArray.addShort(invertSign((Short) v));
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
        public Short fromByteArray(byte[] b, ColumnDefinition cd) {
            return invertSign((short) (((b[0] & 0xFF) << 8) + (b[1] & 0xFF)));
        }
    }

    static class IntegerColumnSerializer implements ColumnSerializer<Integer> {
        @Override
        public Integer deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return invertSign(byteArray.getInt());
        }

        @Override
        public void serialize(ByteArray byteArray, Integer v) {
            byteArray.addInt(invertSign(v));
        }

        @Override
        public Integer deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return invertSign(byteBuf.getInt());
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Integer v) {
            byteBuf.putInt(invertSign(v));
        }
    }

    
    static class LongColumnSerializer implements ColumnSerializer<Long> {
        @Override
        public Long deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return invertSign(byteArray.getLong());
        }

        @Override
        public Long deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return invertSign(byteBuf.getLong());
        }

        @Override
        public void serialize(ByteArray byteArray, Long v) {
            byteArray.addLong(invertSign(v));
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Long v) {
            byteBuf.putLong(invertSign(v));
        }
    }

    static class DoubleColumnSerializer implements ColumnSerializer<Double> {
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

        @Override
        public Double deserialize(ByteArray byteArray, ColumnDefinition cd) {
            return longToDouble(byteArray.getLong());
        }

        @Override
        public Double deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            return longToDouble(byteBuf.getLong());
        }

        @Override
        public void serialize(ByteArray byteArray, Double v) {
            byteArray.addLong(doubleToLong(v));
        }

        @Override
        public void serialize(ByteBuffer byteBuf, Double v) {
            byteBuf.putLong(doubleToLong(v));
        }
    }

    
    static class HresTimestampColumnSerializer implements ColumnSerializer<Instant> {
      //picos is always positive, no need to invert the sign
        @Override
        public Instant deserialize(ByteArray byteArray, ColumnDefinition cd) {
            long millis = invertSign(byteArray.getLong());
            int picos = byteArray.getInt(); 
            return Instant.get(millis, picos);
        }

        @Override
        public Instant deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            long millis = invertSign(byteBuf.getLong());
            int picos = byteBuf.getInt();
            return Instant.get(millis, picos);
        }

        @Override
        public void serialize(ByteArray byteArray, Instant v) {
            byteArray.addLong(invertSign(v.getMillis()));
            byteArray.addInt(v.getPicos());
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
        public Instant fromByteArray(byte[] b, ColumnDefinition cd) {
            long millis = invertSign(ByteArrayUtils.decodeLong(b, 0));
            int picos = ByteArrayUtils.decodeInt(b, 8);
            return Instant.get(millis, picos);
        }
    }

    
    static class UUIDColumnSerializer implements ColumnSerializer<java.util.UUID> {
        @Override
        public java.util.UUID deserialize(ByteArray byteArray, ColumnDefinition cd) {
            long msb = invertSign(byteArray.getLong());
            long lsb = invertSign(byteArray.getLong());
            return new java.util.UUID(msb, lsb);
        }

        @Override
        public java.util.UUID deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            long msb = invertSign(byteBuf.getLong());
            long lsb = invertSign(byteBuf.getLong());
            return new java.util.UUID(msb, lsb);
        }

        @Override
        public void serialize(ByteArray byteArray, java.util.UUID v) {
            byteArray.addLong(invertSign(v.getMostSignificantBits()));
            byteArray.addLong(invertSign(v.getLeastSignificantBits()));
        }

        @Override
        public void serialize(ByteBuffer byteBuf, java.util.UUID v) {
            byteBuf.putLong(invertSign(v.getMostSignificantBits()));
            byteBuf.putLong(invertSign(v.getLeastSignificantBits()));
        }
    }

    static class ArrayColumnSerializer implements ColumnSerializer<java.util.List> {
        ColumnSerializer elementSerializer;

        public ArrayColumnSerializer(ColumnSerializer elementSerializer) {
            this.elementSerializer = elementSerializer;
        }
        @Override
        public List deserialize(ByteArray array, ColumnDefinition cd) {
            int length = array.getInt();
            int position = array.position();
            if (length > maxBinaryLength) {
                throw new YarchException(
                        "binary length " + length + " greater than maxBinaryLenght " + maxBinaryLength);
            }
            if (length > array.size() - position) {
                throw new DatabaseCorruptionException(
                        " " + length + " greater than available data " + (array.size() - position));
            }
            List<Object> list = new ArrayList<Object>();
            while (array.position() - position < length) {
                Object o = elementSerializer.deserialize(array, cd);
                list.add(o);
            }

            return list;
        }

        @Override
        public List deserialize(ByteBuffer byteBuf, ColumnDefinition cd) {
            int length = byteBuf.getInt();
            int position = byteBuf.position();
            if (length > maxBinaryLength) {
                throw new YarchException(
                        "binary length " + length + " greater than maxBinaryLenght " + maxBinaryLength);
            }
            if (length > byteBuf.remaining()) {
                throw new DatabaseCorruptionException(
                        " " + length + " greater than available data " + byteBuf.remaining());
            }
            List<Object> list = new ArrayList<Object>();
            while(byteBuf.position()-position<length) {
                Object o = elementSerializer.deserialize(byteBuf, cd);
                list.add(o);
            }
            
            return list;
        }

        @Override
        public void serialize(ByteArray array, List v) {
            int position = array.size();
            array.addInt(0);
            for (Object o : v) {
                elementSerializer.serialize(array, o);
            }
            int size = array.size() - position - 4;
            array.setInt(position, size);
        }

        @Override
        public void serialize(ByteBuffer byteBuf, List v) throws BufferOverflowException {
            int position = byteBuf.position();
            byteBuf.putInt(0);
            for (Object o : v) {
                elementSerializer.serialize(byteBuf, o);
            }
            int size = byteBuf.position() - position - 4;
            byteBuf.putInt(position, size);
        }
    }

}
