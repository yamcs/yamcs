package org.yamcs.utils;

import java.util.Arrays;
import java.util.PrimitiveIterator;

/**
 * sorted int array
 * 
 * 
 * @author nm
 *
 */
public class SortedIntArray {
  
    public static int DEFAULT_CAPACITY = 10;
    private int[] a;
    private int length;
    
    //caches the hashCode
    private int hash;
    
    
    /**
     * Creates a sorted int array with a default initial capacity
     * 
     */
    public SortedIntArray() {
        a = new int[DEFAULT_CAPACITY];
    }
    
    /**
     * Creates a sorted int array with a given initial capacity
     * 
     * @param capacity
     */
    public SortedIntArray(int capacity) {
        a = new int[capacity];
    }
    
    /**
     * Creates the SortedIntArray by copying all values from the input array and sorting them
     * 
     * @param array
     */
    public SortedIntArray(int... array) {
        length = array.length;
        a = Arrays.copyOf(array, length);
        Arrays.sort(a);
    }

    /**
     * Inserts value to the array and return the position on which has been inserted
     * 
     * @param x - value to be inserted
     */
    public int insert(int x) {
        int pos = Arrays.binarySearch(a, 0, length, x);
        if( pos<0 ) pos = -pos-1;

        ensureCapacity(length+1);
        
        System.arraycopy(a, pos, a, pos+1, length-pos);
        a[pos] = x;
        length++;
        
        return pos;
    }

    /**
     * performs a binary search in the array. 
     * 
     * @see java.util.Arrays#binarySearch(int[], int)
     * @param x
     * @return
     */
    public int search(int x) {
        return  Arrays.binarySearch(a, 0, length, x);
    }
    /**
     * get element at position
     * @param pos
     * @return
     */
    public int get(int pos) {
        if(pos >= length) throw new IndexOutOfBoundsException("Index: "+pos+" length: "+length);
        
        return a[pos];
    }
    
    private void ensureCapacity(int minCapacity) {
        if(minCapacity<=a.length) return;

        int capacity = a.length;
        int newCapacity = capacity + (capacity >> 1);
        if(newCapacity<minCapacity) newCapacity = minCapacity;
        
        a = Arrays.copyOf(a, newCapacity);
    }
  

    public boolean isEmpty() {	
        return a.length==0;
    }

    public int[] getArray() {
        return a;
    }
    public int size() {
        return length;
    }

    /**
     * Constructs an ascending iterator starting from a specified value (inclusive) 
     * 
     * @param startFrom
     * @return
     */
    public PrimitiveIterator.OfInt getAscendingIterator(int startFrom) {
        return new PrimitiveIterator.OfInt() {
            int pos;
            {
                pos = search(startFrom);
                if(pos<0) {
                    pos = -pos-1;
                }
            }
            @Override
            public boolean hasNext() {
                return pos < length;
            }
            
            @Override
            public int nextInt() {
                return a[pos++];
                
            }
        };
    }
    
    /**
     * Constructs an descending iterator starting from a specified value (exclusive) 
     * 
     * @param startFrom
     * @return
     */
    public PrimitiveIterator.OfInt getDescendingIterator(int startFrom) {
        return new PrimitiveIterator.OfInt() {
            int pos;
            {
                pos = search(startFrom);
                if(pos<0) {
                    pos = -pos-1;
                }
                pos--;
            }
            @Override
            public boolean hasNext() {
                return pos>=0;
            }
            
            @Override
            public int nextInt() {
                return a[pos--];
                
            }
        };
    }
    
    public String toString() {        
        StringBuilder b = new StringBuilder();
        int n = length-1;
        
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if(i==n)
                return b.append(']').toString();
            b.append(", ");
        }
    }
    
    
    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && length > 0) {
            h = 1;
            
            for (int i = 0; i < length; i++) {
                h = 31 * h + a[i];
            }
            hash = h;
        }
        return h;
    }
    
    
    public byte[] encodeToVarIntArray() {
        byte[] buf = new byte[length*4];
        
        if(length==0) return buf;
        
        int pos = VarIntUtil.writeVarint32(buf, 0, a[0]);
        
        for(int i=1; i<length; i++) {
            pos = VarIntUtil.writeVarint32(buf, pos, (a[i]-a[i-1]));
        }
        if(pos==buf.length) {
            return buf;
        } else {
            return Arrays.copyOf(buf, pos);
        }
        
    }
    
    public static SortedIntArray decodeFromVarIntArray(byte[] buf) {
        if(buf.length==0) return new SortedIntArray(0);
        SortedIntArray sia = new SortedIntArray();
        
        VarIntUtil.ArrayDecoder ad = VarIntUtil.newArrayDecoder(buf);
        int s=0;
        while(ad.hasNext()) {
            s+=ad.next();
            sia.insert(s);
        }
        return sia;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        
        if (obj == null) return false;
        
        if (getClass() != obj.getClass()) return false;
        
        SortedIntArray other = (SortedIntArray) obj;
        if (length != other.length) return false;
        
        for(int i=0; i<length; i++) {
            if(a[i]!=other.a[i])    return false;
        }
       
        return true;
    }
    
    /**
     * returns true if this array contains the value
     * @param x - value to check
     * 
     */
    public boolean contains(int x) {
        return Arrays.binarySearch(a, 0, length, x)>=0;
    }

   

}
