package org.yamcs.utils;

import java.util.Arrays;

public class ByteArrayUtils {
    
    
    /**
     * returns true if a starts with b
     * @param a
     * @param b
     * @return true if a and b are not null, a.length>=b.length and a[i]=b[i] for i=0...b.length-1
     * @throws NullPointerException if any of them is null
     */
    static public boolean startsWith(byte[] a, byte[] b) {
        
        if(a.length<b.length) return false;
        
        for(int i=0; i<b.length; i++) {
            if(a[i]!=b[i]) return false;
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
     * @return true if a.length>=n, b.length >=n and a[i]==b[i] for i=0..n-1
     * @throws NullPointerException if any of them is null
     */
    static public boolean equalsPrefix(byte[] a, byte b[], int n) {
        if(a.length<n || b.length<n) return false;
        
        for(int i=0;i<n; i++) {
            if(a[i]!=b[i]) return false;
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
}
