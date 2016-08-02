package org.yamcs.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class VarIntUtil {
    /**
     * Encodes x as varint in the buffer at position pos and returns the new position
     * 
     * @param buf
     * @param x
     * @return the decoded integer
     */
    static public int writeVarint32(byte[] buf, int pos, int x) {
        while ((x & ~0x7F) != 0) {
            buf[pos++] = ((byte)((x & 0x7F) | 0x80));
            x >>>= 7;
        }
        buf[pos++] = (byte)(x & 0x7F);

        return pos;
    }

    public static void writeVarInt32(ByteBuffer bb, int x) {
        while ((x & ~0x7F) != 0) {
            bb.put((byte)((x & 0x7F) | 0x80));
            x >>>= 7;
        }
        bb.put((byte)(x & 0x7F));
    }

    public static void writeVarInt64(ByteBuffer bb, long x) {
        while ((x & ~0x7F) != 0) {
            bb.put((byte)((x & 0x7F) | 0x80));
            x >>>= 7;
        }
        bb.put((byte)(x & 0x7F));
    }

    public static int readVarInt32(ByteBuffer bb) throws DecodingException {
        byte b = bb.get();
        int v = b &0x7F;
        for (int shift = 7; (b & 0x80) != 0; shift += 7) {
            if(shift>28) throw new DecodingException("Invalid VarInt32: more than 5 bytes!");

            b = bb.get();
            v |= (b & 0x7F) << shift;

        }
        return v;
    }

    public static long readVarInt64(ByteBuffer bb) {
        byte b = bb.get();
        long v = b &0x7F;
        for (int shift = 7; (b & 0x80) != 0; shift += 7) {
            b = bb.get();
            v |= (b & 0x7F) << shift;
        }
        return v;
    }


    public static void writeSignedVarint32(ByteBuffer bb, int x) {
        writeVarInt32(bb, encodeZigZag(x));
    }

    public static int readSignedVarInt32(ByteBuffer bb) throws DecodingException {
        return decodeZigZag(readVarInt32(bb));
    }

    //same as above but better for negative numbers
    static public int encodeSigned(byte[] buf, int pos, int x) {
        return writeVarint32(buf, pos, encodeZigZag(x));       
    }

    /**
     * decodes an array of varints
     * 
     * @author nm
     *
     */
    public static class ArrayDecoder {
        int pos=0; 
        final byte[] buf;
        private ArrayDecoder(byte[] buf) {
            this.buf = buf;
        }

        /**
         * Returns true if the array contains another element. 
         *  If the array is corrupted, this will return true and next() will throw an BufferOverflow exception
         * @return
         */
        public boolean hasNext() {
            return pos < buf.length;
        }

        public int next() {
            byte b = buf[pos++];
            int v = b &0x7F;
            for (int shift = 7; (b & 0x80) != 0; shift += 7) {
                b = buf[pos++];
                v |= (b & 0x7F) << shift;
            }
            return v;
        }
    }



    public static class SignedArrayDecoder extends ArrayDecoder{
        private SignedArrayDecoder(byte[] buf) {
            super(buf);
        }

        public int next() {
            return decodeZigZag(super.next());
        }
    }

    static public ArrayDecoder newArrayDecoder(byte[] buf) {
        return new ArrayDecoder(buf);
    }



    //used to transform small signed integers into unsigned (see protobuf docs)
    public static int decodeZigZag(int x) {
        return (x >>> 1) ^ -(x & 1);
    }

    public static int encodeZigZag(int x) {
        return (x << 1) ^ (x >> 31);
    }

    public static void writeSizeDelimitedString(ByteBuffer bb, String s) {
        byte[]b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt32(bb,  b.length);
        bb.put(b);
    }

    public static String readSizeDelimitedString(ByteBuffer bb) throws DecodingException {
        int l = readVarInt32(bb);
        byte[] b = new byte[l];
        bb.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    //return a zigzag encoding of deltas of deltas
    // -> if the values in x are close to each-other or are increasing by a constant factor (think counters)
    // this result in an array of small numbers
    public static int[] encodeDeltaDeltaZigZag(int x[]) {
        int n = x.length;
        int[] ddz = new int[n];
        if(n>0) {
            ddz[0] = encodeZigZag(x[0]);
            int d = 0;
            for(int i=1; i<n; i++) {
                int d1 = x[i]-x[i-1];
                ddz[i] = encodeZigZag(d1-d);
                d=d1;
            }
        }
        return ddz;
    }
    
    
    // this is the reverse of the above
    public static int[] decodeDeltaDeltaZigZag(int ddz[]) {
        int n = ddz.length;
        int[] x = new int[n];
        if(n>0) {
            x[0] = decodeZigZag(ddz[0]);
            int d = 0;
            for(int i=1; i<n; i++) {
                d = d + decodeZigZag(ddz[i]);
                x[i] = x[i-1] + d;
            }
        }
        return x;
    }

    //encoding of SortedIntArray in deltas of deltas
    public static int[] encodeDeltaDeltaZigZag(SortedIntArray a) {
        int n = a.size();
        int[] ddz = new int[n];
        if(n>0) {
            ddz[0] = encodeZigZag(a.get(0));
            int d = 0;
            for(int i=1; i<n; i++) {
                int d1 = a.get(i)-a.get(i-1);
                ddz[i] = encodeZigZag(d1-d);
                d=d1;
            }
        }
        return ddz;
    }
    
    public static int[] encodeDeltaDeltaZigZag(IntArray a) {
        int n = a.size();
        int[] ddz = new int[n];
        if(n>0) {
            ddz[0] = encodeZigZag(a.get(0));
            int d = 0;
            for(int i=1; i<n; i++) {
                int d1 = a.get(i)-a.get(i-1);
                ddz[i] = encodeZigZag(d1-d);
                d=d1;
            }
        }
        return ddz;
    }
    /**
     * get the number of bytes necessary to encode value
     * @param size
     * @return
     */
    public static int getEncodedSize(int size) {
        if(size<128) return 1;
        if(size<16384) return 2;
        if(size<2097152) return 3;
        if(size<268435456) return 4;
        return 5;
    }

    
    
}
