package org.yamcs.utils;

import java.util.Arrays;
import java.nio.ByteBuffer;

public class ByteArrayUtils {
    public static final byte[] EMPTY = new byte[0];

    /**
     * If the array is considered binary representation of an integer, add 1 to the integer and returns the
     * corresponding binary representation.
     * 
     * In case an overflow is detected (if the initial array was all 0XFF) an IllegalArgumentException is thrown.
     * 
     * @param a
     * @return a+1
     */
    static public byte[] plusOne(byte[] a) {
        byte[] b = Arrays.copyOf(a, a.length);
        int i = b.length - 1;
        while (i >= 0 && b[i] == -1) {
            b[i] = 0;
            i--;
        }
        if (i == -1) {
            throw new IllegalArgumentException("overflow");
        } else {
            b[i] = (byte) (1 + ((b[i] & 0xFF)));
        }
        return b;
    }

    static public byte[] minusOne(byte[] a) {
        byte[] b = Arrays.copyOf(a, a.length);
        int i = b.length - 1;
        while (i >= 0 && b[i] == 0) {
            b[i] = (byte) 0xFF;
            i--;
        }
        if (i == -1) {
            throw new IllegalArgumentException("underflow");
        } else {
            b[i] = (byte) (((b[i] & 0xFF) - 1));
        }
        return b;
    }

    /**
     * lexicographic comparison which returns 0 if one of the array is a subarray of the other one
     * 
     * @param a1
     * @param a2
     */
    static public int compare(byte[] a1, byte[] a2) {
        for (int i = 0; i < a1.length && i < a2.length; i++) {
            int d = (a1[i] & 0xFF) - (a2[i] & 0xFF);
            if (d != 0) {
                return d;
            }
        }
        return 0;
    }


    /**
     * write an long into a byte array at offset and returns the array
     */
    public static byte[] encodeLong(long x, byte[] a, int offset) {
        a[offset] = (byte) (x >> 56);
        a[offset + 1] = (byte) (x >> 48);
        a[offset + 2] = (byte) (x >> 40);
        a[offset + 3] = (byte) (x >> 32);
        a[offset + 4] = (byte) (x >> 24);
        a[offset + 5] = (byte) (x >> 16);
        a[offset + 6] = (byte) (x >> 8);
        a[offset + 7] = (byte) (x);

        return a;
    }

    public static byte[] encodeLongLE(long x, byte[] a, int offset) {
        a[offset] = (byte) (x);
        a[offset + 1] = (byte) (x >> 8);
        a[offset + 2] = (byte) (x >> 16);
        a[offset + 3] = (byte) (x >> 24);
        a[offset + 4] = (byte) (x >> 32);
        a[offset + 5] = (byte) (x >> 40);
        a[offset + 6] = (byte) (x >> 48);
        a[offset + 7] = (byte) (x >> 56);

        return a;
    }

    public static long decodeCustomInteger(byte[] a, int offset, int length) {
        if (offset < 0 || length <= 0 || offset + length > a.length) {
            throw new IllegalArgumentException("Invalid offset or length");
        }

        long result = 0;
        for (int i = 0; i < length; i++) {
            result = (result << 8) | (a[offset + i] & 0xFF);
        }

        return result;
    }

    public static byte[] encodeCustomInteger(long value, int size) {
        if (size < 1 || size > 8) {
            throw new IllegalArgumentException("Byte size must be between 1 and 8");
        }
        
        byte[] bb = new byte[size];
        for (int i = (size - 1); i >= 0; i--) {
            bb[size - (i + 1)] = (byte) (value >> (i * 8));
        }

        return bb;
    }

    /**
     * write a long in to a byte array of 8 bytes
     * 
     * @param x
     * @return
     */
    public static byte[] encodeLong(long x) {
        byte[] toReturn = new byte[8];
        return encodeLong(x, toReturn, 0);
    }

    // ----------------- 8 bytes(long) encoding/decoding

    public static long decodeLong(byte[] a, int offset) {
        return ((a[offset] & 0xFFl) << 56) +
                ((a[offset + 1] & 0xFFl) << 48) +
                ((a[offset + 2] & 0xFFl) << 40) +
                ((a[offset + 3] & 0xFFl) << 32) +
                ((a[offset + 4] & 0xFFl) << 24) +
                ((a[offset + 5] & 0xFFl) << 16) +
                ((a[offset + 6] & 0xFFl) << 8) +
                ((a[offset + 7] & 0xFFl));
    }

    public static long decodeLongLE(byte[] a, int offset) {
        return ((a[offset] & 0xFFl) +
                ((a[offset + 1] & 0xFFl) << 8) +
                ((a[offset + 2] & 0xFFl) << 16) +
                ((a[offset + 3] & 0xFFl) << 24) +
                ((a[offset + 4] & 0xFFl) << 32) +
                ((a[offset + 5] & 0xFFl) << 40) +
                ((a[offset + 6] & 0xFFl) << 48) +
                ((a[offset + 7] & 0xFFl) << 56));
    }

    // ----------------- 7 bytes encoding/decoding
    public static long decodeUnsigned7Bytes(byte[] a, int offset) {
        return ((a[offset] & 0xFFl) << 48) +
                ((a[offset + 1] & 0xFFl) << 40) +
                ((a[offset + 2] & 0xFFl) << 32) +
                ((a[offset + 3] & 0xFFl) << 24) +
                ((a[offset + 4] & 0xFFl) << 16) +
                ((a[offset + 5] & 0xFFl) << 8) +
                ((a[offset + 6] & 0xFFl));
    }

    // ----------------- 6 bytes encoding/decoding
    public static byte[] encodeUnsigned6Bytes(long x, byte[] a, int offset) {
        a[offset] = (byte) (x >> 40);
        a[offset + 1] = (byte) (x >> 32);
        a[offset + 2] = (byte) (x >> 24);
        a[offset + 3] = (byte) (x >> 16);
        a[offset + 4] = (byte) (x >> 8);
        a[offset + 5] = (byte) (x);

        return a;
    }

    /**
     * Decode 6 bytes as an unsigned integer
     * 
     * @param a
     * @param offset
     * @return
     */
    public static long decodeUnsigned6Bytes(byte[] a, int offset) {
        return ((a[offset] & 0xFFl) << 40) +
                ((a[offset + 1] & 0xFFl) << 32) +
                ((a[offset + 2] & 0xFFl) << 24) +
                ((a[offset + 3] & 0xFFl) << 16) +
                ((a[offset + 4] & 0xFFl) << 8) +
                ((a[offset + 5] & 0xFFl));
    }

    /**
     * Decode 6 bytes as a signed integer in two's complement encoding
     * 
     * @param a
     * @param offset
     * @return
     */
    public static long decode6Bytes(byte[] a, int offset) {
        long x = decodeUnsigned6Bytes(a, offset);
        if (a[offset] < 0) {
            return x - 0x10000_00000000l;
        } else {
            return x;
        }
    }

    // ----------------- 5 bytes encoding/decoding
    /**
     * Decode 5 bytes as a signed integer in two's complement encoding
     * 
     * @param a
     * @param offset
     * @return
     */
    public static long decode5Bytes(byte[] a, int offset) {
        long x = decodeUnsigned5Bytes(a, offset);
        if (a[offset] < 0) {
            return x - 0x100_00000000l;
        } else {
            return x;
        }
    }

    public static byte[] encodeUnsigned5Bytes(long x, byte[] a, int offset) {
        a[offset] = (byte) (x >> 32);
        a[offset + 1] = (byte) (x >> 24);
        a[offset + 2] = (byte) (x >> 16);
        a[offset + 3] = (byte) (x >> 8);
        a[offset + 4] = (byte) (x);

        return a;
    }

    public static long decodeUnsigned5Bytes(byte[] a, int offset) {
        return ((a[offset] & 0xFFl) << 32) +
                ((a[offset + 1] & 0xFFl) << 24) +
                ((a[offset + 2] & 0xFFl) << 16) +
                ((a[offset + 3] & 0xFFl) << 8) +
                ((a[offset + 4] & 0xFFl));
    }

    // ----------------- 4 bytes(int) encoding/decoding
    /**
     * write an int into a byte array at offset and returns the array
     */
    public static byte[] encodeInt(int x, byte[] a, int offset) {
        a[offset] = (byte) (x >> 24);
        a[offset + 1] = (byte) (x >> 16);
        a[offset + 2] = (byte) (x >> 8);
        a[offset + 3] = (byte) (x);

        return a;
    }

    public static byte[] encodeInt(int x) {
        byte[] toReturn = new byte[4];
        return encodeInt(x, toReturn, 0);
    }

    public static int decodeInt(byte[] a, int offset) {
        return ((a[offset] & 0xFF) << 24) +
                ((a[offset + 1] & 0xFF) << 16) +
                ((a[offset + 2] & 0xFF) << 8) +
                ((a[offset + 3] & 0xFF));
    }

    public static int decodeIntLE(byte[] a, int offset) {
        return ((a[offset + 3] & 0xFF) << 24) +
                ((a[offset + 2] & 0xFF) << 16) +
                ((a[offset + 1] & 0xFF) << 8) +
                ((a[offset] & 0xFF));
    }

    // ----------------- 3 bytes encoding/decoding
    public static byte[] encodeUnsigned3Bytes(int x, byte[] a, int offset) {
        a[offset] = (byte) (x >> 16);
        a[offset + 1] = (byte) (x >> 8);
        a[offset + 2] = (byte) (x);

        return a;
    }

    public static int decodeUnsigned3Bytes(byte[] a, int offset) {
        return ((a[offset] & 0xFF) << 16) +
                ((a[offset + 1] & 0xFF) << 8) +
                ((a[offset + 2] & 0xFF));
    }

    public static int decodeUnsigned3BytesLE(byte[] a, int offset) {
        return ((a[offset + 2] & 0xFF) << 16) +
                ((a[offset + 1] & 0xFF) << 8) +
                ((a[offset] & 0xFF));
    }

    // ----------------- 2 bytes(short) encoding/decoding
    public static byte[] encodeUnsignedShort(int x, byte[] a, int offset) {
        a[offset] = (byte) (x >> 8);
        a[offset + 1] = (byte) (x);

        return a;
    }

    public static short decodeShort(byte[] a, int offset) {
        int x = ((a[offset] & 0xFF) << 8) +
                ((a[offset + 1] & 0xFF));
        return (short) x;
    }

    public static int decodeUnsignedShort(byte[] a, int offset) {
        int x = ((a[offset] & 0xFF) << 8) +
                ((a[offset + 1] & 0xFF));
        return x;
    }

    /**
     * Decode unsigned short
     * 
     * @param a
     * @param offset
     * @return
     */
    public static int decodeUnsignedShortLE(byte[] a, int offset) {
        return ((a[offset + 1] & 0xFF) << 8) +
                ((a[offset] & 0xFF));
    }
}
