package org.yamcs.utils;

import java.util.Arrays;

public class ByteArrayUtils {
    public static final byte[] EMPTY = new byte[0];
    /**
     * returns true if a starts with b
     * @param a
     * @param b
     * @return true if a and b are not null, a.length &ge; b.length and a[i]=b[i] for i=0...b.length-1
     * @throws NullPointerException if any of them is null
     */
    static public boolean startsWith(byte[] a, byte[] b) {
        
        if(a.length<b.length) {
            return false;
        }
        
        for(int i=0; i<b.length; i++) {
            if(a[i]!=b[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 
     * Compares the first n bytes of two arrays. The arrays must be at least n bytes long (otherwise false is returned)
     *  
     * @param a - the first array to compare
     * @param b - the second array to compare
     * @param n - the number of bytes to compare
     * @return true if a.length &gt;= n, b.length &gt;= n and a[i]==b[i] for i=0..n-1
     * @throws NullPointerException if any of them is null
     */
    static public boolean equalsPrefix(byte[] a, byte b[], int n) {
        if(a.length<n || b.length<n) {
            return false;
        }
        
        for(int i=0;i<n; i++) {
            if(a[i]!=b[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * If the array is considered binary representation of an integer, add 1 to the integer and returns the corresponding binary representation.
     * 
     * In case an overflow is detected (if the initial array was all 0XFF) an IllegalArgumentException is thrown.
     * 
     * @param a
     * @return a+1
     */
    static public byte[] plusOne(byte[] a) {
        byte[] b = Arrays.copyOf(a, a.length);
        int i = b.length-1;
        while(i>=0 && b[i]==0xFF) {
            b[i]=0;
            i--;
        }
        if(i==-1)  {
            throw new IllegalArgumentException("overflow");
        } else {
            b[i]= (byte) (1+((b[i]&0xFF)));
        }
        return b;
    }
    
    /**
     * lexicographic comparison which returns 0 if one of the array is a subarray of the other one

     * @param a1
     * @param a2
     */
    static public int compare(byte[] a1, byte[] a2) {
        for(int i=0;i<a1.length && i<a2.length;i++) {
            int d=(a1[i]&0xFF)-(a2[i]&0xFF);
            if(d!=0){
                return d;
            }
        }
        return 0;
    }
    
    /**
     * write an int into a byte array at offset and returns the array
     */
    public static byte[] encodeInt(int x, byte[] a, int offset) {
        a[offset] = (byte)(x >> 24);
        a[offset+1] = (byte)(x >> 16);
        a[offset+2] = (byte)(x >> 8);
        a[offset+3] = (byte)(x);
                
        return a;
    }
    
    /**
     * write an long into a byte array at offset and returns the array
     */
    public static byte[] encodeLong(long x, byte[] a, int offset) {
        a[offset] = (byte)(x >> 56);
        a[offset+1] = (byte)(x >> 48);
        a[offset+2] = (byte)(x >> 40);
        a[offset+3] = (byte)(x >> 32);
        a[offset+4] = (byte)(x >> 24);
        a[offset+5] = (byte)(x >> 16);
        a[offset+6] = (byte)(x >> 8);
        a[offset+7] = (byte)(x);
                
        return a;
    }
    public static long decodeLong(byte[] a, int offset) {
        return ((a[offset]   & 0xFFl) <<56) +
                ((a[offset+1] & 0xFFl) <<48) +
                ((a[offset+2] & 0xFFl) <<40 ) +
                ((a[offset+3] & 0xFFl) <<32 ) +
                ((a[offset+4] & 0xFFl) <<24 ) +
                ((a[offset+5] & 0xFFl) <<16 ) +
                ((a[offset+6] & 0xFFl) <<8 ) +
                ((a[offset+7] & 0xFFl)     );
    }
    
    public static int decodeInt(byte[] a, int offset) {
        return ((a[offset]   & 0xFF) <<24) +
               ((a[offset+1] & 0xFF) <<16) +
               ((a[offset+2] & 0xFF) <<8 ) +
               ((a[offset+3] & 0xFF)     );
    }
    
    public static int decodeShort(byte[] a, int offset) {
        return ((a[offset] & 0xFF) <<8 ) +
               ((a[offset+1] & 0xFF)     );
    }
}
