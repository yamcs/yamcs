package org.yamcs.utils;

import java.util.Arrays;

/**
 * expandable double array
 * 
 * 
 * @author nm
 *
 */
public class DoubleArray {
  
    public static int DEFAULT_CAPACITY = 10;
    private double[] a;
    private int length;
    
    //caches the hashCode
    private int hash;
    
    
    /**
     * Creates a sorted int array with a default initial capacity
     */
    public DoubleArray() {
        a = new double[DEFAULT_CAPACITY];
    }
    
    /**
     * Creates an IntArray with a given initial capacity
     * 
     * @param capacity
     */
    public DoubleArray(int capacity) {
        a = new double[capacity];
    }
    
    /**
     * Creates the IntArray by copying all values from the input array and sorting them
     * 
     * @param array
     */
    private DoubleArray(double... array) {
        length = array.length;
        a = array;
    }

   

    /**
     * add value to the array 
     * 
     * @param x
     */
    public void add(double x) {
        ensureCapacity(length+1);
        a[length] = x;
        length++;
    }
    
    public void add(int pos, double x) {
        if(pos>length) throw new IndexOutOfBoundsException("Index: "+pos+" length: "+length);
        ensureCapacity(length+1);
        System.arraycopy(a, pos, a, pos + 1, length - pos);
        a[pos] = x;
        length++;
    }
    /**
     * get element at position
     * @param pos
     * @return
     */
    public double get(int pos) {
        rangeCheck(pos);

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

    public double[] toArray() {
        return Arrays.copyOf(a, length);
    }
    
    public int size() {
        return length;
    }

    public void set(int pos, double x) {
        rangeCheck(pos);
        a[pos] = x;
    }
    
    private void rangeCheck(int pos) {
        if(pos >= length) throw new IndexOutOfBoundsException("Index: "+pos+" length: "+length);
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
                long bits = Double.doubleToLongBits(a[i]);
                h = 31 * h + (int)(bits ^ (bits >>> 32));
            }
            hash = h;
        }
        return h;
    }
  
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        
        if (obj == null) return false;
        
        if (getClass() != obj.getClass()) return false;
        
        DoubleArray other = (DoubleArray) obj;
        if (length != other.length) return false;
        
        for(int i=0; i<length; i++) {
            if(a[i]!=other.a[i])    return false;
        }
       
        return true;
    }

    public static DoubleArray wrap(double[] doubles) {
        return new DoubleArray(doubles);
    }

    public double[] array() {
        return a;
    }

   
   
}
