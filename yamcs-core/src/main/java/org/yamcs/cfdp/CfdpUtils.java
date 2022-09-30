package org.yamcs.cfdp;

import com.google.common.primitives.Longs;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class CfdpUtils {

    // counting from zero (and big-endian) , is the bitnr't bit of the input byte set?
    public static boolean isBitOfByteSet(Byte input, int bitnr) {
        return getBitOfByte(input, bitnr) == 1;
    }

    // counting from zero (and big-endian) , get the bitnr't bit of the input byte
    public static int getBitOfByte(Byte input, int bitnr) {
        return (input >> (7 - bitnr)) & 1;
    }

    /*
     * get an unsigned byte value from an input buffer, at its current position
     * A byte has range -2ˆ7+1 to 2ˆ7-1, while we want 0 to 2ˆ15-1, the mask and short cast takes care of this
     */
    public static short getUnsignedByte(ByteBuffer buffer) {
        return (short) (buffer.get() & 0xff);
    }

    /*
     * write the given int as an unsigned byte to the given buffer at its current position
     */
    public static void writeUnsignedByte(ByteBuffer buffer, int input) {
        buffer.put(intToUnsignedByte(input));
    }

    /*
     * get the given int as an unsigned byte to the given buffer at its current position
     */
    public static byte intToUnsignedByte(int input) {
        // TODO: would make more sense if char/check > 255
        return (byte) (input & 0xff);
    }

    /*
     * get an unsigned short value from an input buffer, at its current position
     * A short has range -2ˆ15+1 to 2ˆ15-1, while we want 0 to 2ˆ16-1, the mask and int cast takes care of this
     */
    public static int getUnsignedShort(ByteBuffer buffer) {
        return buffer.getShort() & 0xffff;
    }

    /**
     * get an unsigned int value from an input buffer, at its current position A short has range -2ˆ31+1 to 2ˆ31-1,
     * while we want 0 to 2ˆ32-1, the mask and int cast takes care of this
     */
    public static long getUnsignedInt(ByteBuffer buffer) {
        return buffer.getInt() & 0xffffffffl;
    }

    /**
     * Write the given long as an unsigned int to the given buffer at its current position
     */
    public static void writeUnsignedInt(ByteBuffer buffer, long input) {
        buffer.putInt((int) (input & 0xffffffff));
    }

    /*
     * Read nrOfBytesToRead bytes (max 8) from a given buffer at the current position, 
     * return the result as an unsigned long,
     * moves the position of the buffer to after the read bytes
     * 
     * Note that if nrOfBytesToRead > 8, a cap to 8 bytes is done
     * 
     */
    public static Long getUnsignedLongFromBuffer(ByteBuffer buffer, int nrOfBytesToRead) {
        byte[] temp = new byte[java.lang.Math.min(nrOfBytesToRead, 8)];
        buffer.get(temp);
        return getUnsignedLongFromByteArray(temp);
    }

    /**
     * if bool is true, write bit 1 on bitnr position
     */
    public static byte boolToByte(boolean bool, int bitnr) {
        return (byte) (bool ? (1 << (7 - bitnr)) : 0);
    }

    public static long getUnsignedLongFromByteArray(byte[] input) {
        long toReturn = 0;
        for (int i = 0; i < input.length; i++) {
            toReturn <<= 8;
            toReturn |= (input[i] & 0xFF);
        }
        return toReturn;
    }

    public static byte[] longToBytesFixed(long input, int length) {
        byte[] toReturn = new byte[length];
        for (int i = length - 1; i >= 0; i--) {
            toReturn[i] = (byte) (input & 0xFF);
            input >>= 8;
        }
        return toReturn;
    }

    public static byte[] longToTrimmedBytes(long input) {
        byte[] array = Longs.toByteArray(input);
        for (int i = 0; i < array.length; i++) {
            if (array[i]!= 0) {
                return Arrays.copyOfRange(array, i, array.length);
            }
        }
        return new byte[] {0};
    }
}
