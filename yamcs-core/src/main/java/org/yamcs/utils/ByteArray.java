package org.yamcs.utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.util.Arrays;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;

/**
 * byte array which grows and also supports writing/reading int, double, etc.
 * 
 * All non byte operations are big endian.
 * 
 * @author nm
 *
 */
public class ByteArray {
    public static int DEFAULT_CAPACITY = 10;
    private byte[] a;
    private int length;
    private int position = 0;

    /**
     * Creates a sorted int array with a default initial capacity
     */
    public ByteArray() {
        a = new byte[DEFAULT_CAPACITY];
    }

    /**
     * Creates an IntArray with a given initial capacity
     * 
     * @param capacity
     */
    public ByteArray(int capacity) {
        a = new byte[capacity];
    }

    private ByteArray(byte[] a1) {
        a = a1;
        length = a1.length;
    }

    /**
     * Creates the ByteArray with the backing array
     * 
     * @param array
     * @return a new object containing all the values from the passed array
     */
    public static ByteArray wrap(byte... array) {
        return new ByteArray(array);
    }

    public void ensureRemaining(int size) {
        ensureCapacity(length + size);
    }

    /**
     * add value to the array
     * 
     * @param x
     *            - value to be added
     */
    public void add(byte x) {
        ensureCapacity(length + 1);
        a[length] = x;
        length++;
    }

    public void addShort(short x) {
        ensureCapacity(length + 2);
        ByteArrayUtils.encodeUnsignedShort(x, a, length);
        length += 2;
    }

    public void addInt(int x) {
        ensureCapacity(length + 4);
        ByteArrayUtils.encodeInt(x, a, length);
        length += 4;
    }

    public void addLong(long x) {
        ensureCapacity(length + 8);
        ByteArrayUtils.encodeLong(x, a, length);
        length += 8;
    }

    public void add(byte[] v) {
        ensureCapacity(length + v.length);
        System.arraycopy(v, 0, a, length, v.length);
        length += v.length;
    }

    public void addDouble(double x) {
        ensureCapacity(length + 8);
        ByteArrayUtils.encodeLong(Double.doubleToRawLongBits(x), a, length);
        length += 8;
    }

    /**
     * Writes a protobuf message to the buffer. The message will be prefixed by its size in 4 bytes big endian.
     * 
     * @param msg
     */
    public void addSizePrefixedProto(MessageLite msg) {
        int size = msg.getSerializedSize();
        ensureCapacity(length + size + 4);
        addInt(size);
        try {
            msg.writeTo(CodedOutputStream.newInstance(a, length, size));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        length += size;
    }

    /**
     * Writes a string preceded by its size in two bytes big endian.
     * <p>
     * The encoded byte array does not contain null characters
     * 
     * @see DataOutputStream#writeUTF(String)
     * 
     * @param v
     */
    public void addSizePrefixedUTF(String v) {
        ensureCapacity(length + v.length() + 2);

        int pos = length;
        length += 2;
        addUTF(v);

        ByteArrayUtils.encodeUnsignedShort(length - pos - 2, a, pos);
    }

    /**
     * Writes a string encoded in UTF as per {@link DataOutputStream#writeUTF(String)} terminated with a null character
     * 
     * @param v
     */
    public void addNullTerminatedUTF(String v) {
        int strlen = v.length();
        ensureCapacity(length + strlen + 1);
        addUTF(v);
        add((byte) 0);
    }

    private void addUTF(String v) {
        int strlen = v.length();
        int len = 0;
        int c;

        for (int i = 0; i < strlen; i++) {
            c = v.charAt(i);
            if ((c > 0) && (c < 0x80)) {
                add((byte) c);
                len++;
            } else if (c < 0x0800) {// this cover also the null characters (c=0)
                add((byte) (0xC0 | ((c >> 6) & 0x1F)));
                add((byte) (0x80 | ((c >> 0) & 0x3F)));
                len += 2;
            } else {
                add((byte) (0xE0 | ((c >> 12) & 0x0F)));
                add((byte) (0x80 | ((c >> 6) & 0x3F)));
                add((byte) (0x80 | ((c >> 0) & 0x3F)));
                len += 3;
            }
        }

        if (len > 0xFFFF) {
            throw new BufferOverflowException();
        }
    }

    public void insert(int pos, byte x) {
        if (pos > length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }
        ensureCapacity(length + 1);
        System.arraycopy(a, pos, a, pos + 1, length - pos);
        a[pos] = x;
        length++;
    }

    public void set(int pos, byte x) {
        rangeCheck(pos + 1);
        a[pos] = x;
    }

    public void setInt(int pos, int x) {
        rangeCheck(pos + 4);
        ByteArrayUtils.encodeInt(x, a, pos);
    }

    public byte get() {
        rangeCheck(position + 1);
        return a[position++];
    }

    public short getShort() {
        rangeCheck(position + 2);
        short x = ByteArrayUtils.decodeShort(a, position);
        position += 2;
        return x;
    }

    public int getInt() {
        rangeCheck(position + 4);
        int x = ByteArrayUtils.decodeInt(a, position);
        position += 4;
        return x;
    }

    public long getLong() {
        rangeCheck(position + 8);
        long x = ByteArrayUtils.decodeLong(a, position);
        position += 8;
        return x;
    }

    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    public String getSizePrefixedUTF() throws DecodingException {
        int len = getShort() & 0xFFFF;
        return getUTF(position + len, false);
    }

    public String getNullTerminatedUTF() throws DecodingException {
        String s = getUTF(length, true);
        rangeCheck(position + 1);
        position++;
        return s;
    }

    public void get(byte[] bp) {
        rangeCheck(position + bp.length);
        System.arraycopy(a, position, bp, 0, bp.length);
        position += bp.length;
    }

    public <T extends MessageLite.Builder> void getSizePrefixedProto(T builder) {
        int size = getInt();
        try {
            builder.mergeFrom(CodedInputStream.newInstance(a, position, size));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        position += size;
    }

    private String getUTF(int limit, boolean nullTerminated) throws DecodingException {
        char[] ca = new char[limit - position];
        int k = 0;
        int i = position;

        while (i < limit) {
            if (nullTerminated && a[i] == 0) {
                break;
            }

            int char2, char3;
            int c = a[i++] & 0xFF;
            int c4 = c >> 4;
            if (c4 <= 7) {
                ca[k++] = (char) c;
            } else if (c4 == 12 || c4 == 13) {
                if (i + 1 > limit) {
                    throw new DecodingException("invalid UTF8 string at byte" + (i - 1));
                }
                char2 = a[i++] & 0xFF;
                ca[k++] = (char) (((c & 0x1F) << 6) |
                        (char2 & 0x3F));
            } else if (c4 == 14) {
                if (i + 2 > limit) {
                    throw new DecodingException("invalid UTF8 string at byte" + (i - 1));
                }
                char2 = a[i++] & 0xFF;
                char3 = a[i++] & 0xFF;
                ca[k++] = (char) (((c & 0x0F) << 12) |
                        ((char2 & 0x3F) << 6) |
                        ((char3 & 0x3F) << 0));
            } else {
                throw new DecodingException("invalid UTF8 string at byte" + (i - 1));
            }
        }
        position = i;

        // The number of chars produced may be less than utflen
        return new String(ca, 0, k);
    }

    /**
     * get element at position
     * 
     * @param pos
     * @return the element at the specified position
     */
    public byte get(int pos) {
        rangeCheck(pos);
        return a[pos];
    }

    public boolean isEmpty() {
        return a.length == 0;
    }

    /**
     * 
     * @return a copy of the underlying byte array with the current length
     */
    public byte[] toArray() {
        return Arrays.copyOf(a, length);
    }

    public int size() {
        return length;
    }

    private void rangeCheck(int pos) {
        if (pos > length) {
            throw new IndexOutOfBoundsException("Index: " + pos + " length: " + length);
        }
    }

    /**
     * Returns the index of the first occurrence of the specified element in the
     * array, or -1 if the array does not contain the element.
     * 
     * @param x
     *            element which is searched for
     * 
     * @return the index of the first occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element.
     */
    public int indexOf(byte x) {
        for (int i = 0; i < length; i++) {
            if (a[i] == x)
                return i;
        }
        return -1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        ByteArray other = (ByteArray) obj;
        if (length != other.length)
            return false;

        for (int i = 0; i < length; i++) {
            if (a[i] != other.a[i])
                return false;
        }

        return true;
    }

    public void reset() {
        this.length = 0;
    }

    public void reset(int length) {
        if (length > a.length) {
            throw new IllegalArgumentException("length larger than buffer length");
        }
        this.length = length;
    }

    /**
     * get the backing array
     * 
     * @return the backing array
     */
    public byte[] array() {
        return a;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= a.length) {
            return;
        }

        int capacity = a.length;
        int newCapacity = capacity + (capacity >> 1);
        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }

        a = Arrays.copyOf(a, newCapacity);
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        int n = length - 1;
        if (n == -1) {
            return "[]";
        }
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == n)
                return b.append(']').toString();
            b.append(", ");
        }
    }

    public int position() {
        return position;
    }

}
