package org.yamcs.parameter;

import java.util.Arrays;
import java.util.BitSet;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueUtility;

/**
 * Stores parameters of the same type as array
 * 
 * For primitive types, it uses much less memory than having an Value[]
 * @author nm
 *
 */
public class ValueArray {
    static final int INITIAL_CAPACITY = 16;
    
    final Type type;
    Object obj;
    int size;

    public ValueArray(Type type, int size) {
        this.type = type;
        obj = newObj(type, size);
        this.size = size;
    }

    public ValueArray(Type type) {
        this(type, INITIAL_CAPACITY);
        
    }
   
    public ValueArray(Type type, int[] r) {
        this.type = type;
        this.obj = r;
        this.size = r.length;
    }

    public ValueArray(Type type, long[] r) {
        this.type = type;
        this.obj = r;
        this.size = r.length;
    }

    public ValueArray(double[] r) {
        this.type = Type.DOUBLE;
        this.obj = r;
        this.size = r.length;
    }
    
    public ValueArray(float[] r) {
        this.type = Type.FLOAT;
        this.obj = r;
        this.size = r.length;
    }

    public ValueArray(byte[][] r) {
        this.type = Type.BINARY;
        this.obj = r;
        this.size = r.length;
    }


    public ValueArray(Type type, Object[] r) {
        this.type = type;
        this.obj = r;
        this.size = r.length;
    }

    public ValueArray(BitSet bitset, int size) {
        this.type = Type.BOOLEAN;
        this.obj = bitset;
        this.size = size;
    }

    public ValueArray(String[] r) {
        this(Type.STRING, Arrays.copyOf(r, r.length, Object[].class));
    }

    public void setValue(int idx, boolean b) {
        if(type!=Type.BOOLEAN) {
            throw new IllegalArgumentException("This array is not of boolean type but "+type);
        }
        ((BitSet) obj).set(idx, b);
    }

    public void setValue(int idx, Value v) {
        if(this.type!=v.getType()) {
            throw new IllegalArgumentException("Expected type "+this.type+" got: "+v.getType());
        }
        
        switch (type) {
        case DOUBLE:
            ((double[]) obj)[idx] = v.getDoubleValue();
            break;
        case FLOAT:
            ((float[]) obj)[idx] = v.getFloatValue();
            break;
        case SINT32:
            ((int[]) obj)[idx] = v.getSint32Value();
            break;
        case UINT32:
            ((int[]) obj)[idx] = v.getUint32Value();
            break;
        case SINT64:
            ((long[]) obj)[idx] = v.getSint64Value();
            break;
        case UINT64:
            ((long[]) obj)[idx] = v.getUint64Value();
            break;
        case TIMESTAMP:
            ((long[]) obj)[idx] = v.getTimestampValue();
            break;
        case STRING:
        case ENUMERATED:
            ((Object[]) obj)[idx] = v.getStringValue();
            break;
        case BINARY:
            ((Object[]) obj)[idx] = v.getBinaryValue();
            break;
        case BOOLEAN:
            ((BitSet) obj).set(idx, v.getBooleanValue());
            break;
        default:
            throw new IllegalStateException("Unknown type " + type);
        }
    }
    
    
    public Value getValue(int idx) {
        switch (type) {
        case BOOLEAN:
            return ValueUtility.getBooleanValue(((BitSet) obj).get(idx));
        case DOUBLE:
            return ValueUtility.getDoubleValue(((double[]) obj)[idx]);
        case FLOAT:
            return ValueUtility.getFloatValue(((float[]) obj)[idx]);
        case SINT32:
            return ValueUtility.getSint32Value(((int[]) obj)[idx]);
        case UINT32:
            return ValueUtility.getUint32Value(((int[]) obj)[idx]);
        case SINT64:
            return ValueUtility.getSint64Value(((long[]) obj)[idx]);
        case UINT64:
            return ValueUtility.getUint64Value(((long[]) obj)[idx]);
        case TIMESTAMP:
            return ValueUtility.getTimestampValue(((long[]) obj)[idx]);
        case STRING:
        case ENUMERATED:
            return ValueUtility.getStringValue((String)((Object[]) obj)[idx]);
        case BINARY:
            return ValueUtility.getBinaryValue((byte[]) (((Object[]) obj)[idx]));
        default:
            throw new IllegalStateException("Unknown type " + type);
        }
    }
    
    
    private static Object newObj(Type type, int size) {
        switch (type) {
        case BOOLEAN:
            return new BitSet(size);
        case DOUBLE:
            return new double[size];
        case FLOAT:
            return new float[size];
        case SINT32:
        case UINT32:
            return new int[size];
        case SINT64:
        case UINT64:
        case TIMESTAMP:
            return new long[size];
        case STRING:
        case ENUMERATED:
            return new Object[size];
        case BINARY:
            return new Object[size];
        default:
            throw new IllegalStateException("Unknown type " + type);
        }

    }

    public Type getType() {
        return type;
    }
    /**
     * get the array as an int[].
     * Throws a {@link ClassCastException} if the array's type is not one of {@link Type#UINT32} or {@link Type#SINT32}
     * @return
     */
    public int[] getIntArray() {
        return (int[])obj;
    }
    /**
     * get the array as an long[].
     * Throws a {@link ClassCastException} if the array's type is not one of {@link Type#UINT64}, {@link Type#SINT64} or {@link Type#TIMESTAMP}
     * @return
     */
    public long[] getLongArray() {
        return (long[])obj;
    }

    /**
     * get the array as an float[].
     * Throws a {@link ClassCastException} if the array is not of {@link Type#FLOAT} type
     * @return
     */
    public float[] getFloatArray() {
        return (float[])obj;
    }

    /**
     * get the array as an double[].
     * Throws a {@link ClassCastException} if the array is not of {@link Type#DOUBLE} type
     * @return
     */
    public double[] getDoubleArray() {
        return (double[])obj;
    }
    
    public int size() {
        return size;
    }
    
    /**
     * merges the value arrays srcValueArray into a new array based on idx.
     * 
     * The returned array has the size of the sum of the sizes of srcValueArray arrays.
     * 
     * The src[] array has the length of the returned array and for each element i of the returned array src[i] says which of the inputValueArray is used.   
     * 
     * The types of the inputValueArray arrays have to be the same and that will also be the type of the returned array.
     * 
     * @param src - an array indicating which from the srcValueArray is the source of the data for each index
     * @param srcValueArray the source elements
     * @return a new array representing the merge of the input arrays
     */
    static public ValueArray merge(int[] src, ValueArray...srcValueArray) {
        
        ValueArray va0 = srcValueArray[0];
        Type type = va0.getType();
        int length = va0.size();
        for(int i=1; i<srcValueArray.length; i++) {
            if(srcValueArray[i].getType()!=type) {
                throw new IllegalArgumentException("The input arrays have to be all of the same type");
            }
            length+=srcValueArray[i].size();
        }
        if(length!=src.length) {
            throw new IllegalArgumentException("The length of n has to be the sum of the sizes of the input arrays");
        }

        
        if(va0.obj instanceof int[]) {
            return new ValueArray(type, mergeIntArrays(src, srcValueArray));
        } else if(va0.obj instanceof long[]) {
            return new ValueArray(type, mergeLongArrays(src, srcValueArray));
        } else if(va0.obj instanceof double[]) {
            return new ValueArray(mergeDoubleArrays(src, srcValueArray));
        } else if(va0.obj instanceof float[]) {
            return new ValueArray(mergeFloatArrays(src, srcValueArray));
        } else if(va0.obj instanceof Object[]) {
            return new ValueArray(type, mergeObjectArrays(src, srcValueArray));
        } else if(va0.obj instanceof BitSet) {
            return new ValueArray(mergeBitsets(src, srcValueArray), length);
        }
        
        return null;
    }
    
 

    static private int[] mergeIntArrays(int[] n, ValueArray...inputValueArray) {
        int[] idx = new int[inputValueArray.length];
        int [] r = new int[n.length];
        for(int i= 0; i<n.length; i++) {
            int src = n[i];
            r[i] = ((int[])inputValueArray[src].obj)[idx[src]];
            idx[src]++;
        }
        return r;
    }
    
    static private long[] mergeLongArrays(int[] n, ValueArray...inputValueArray) {
        int[] idx = new int[inputValueArray.length];
        long [] r = new long[n.length];
        for(int i= 0; i<n.length; i++) {
            int src = n[i];
            r[i] = ((long[])inputValueArray[src].obj)[idx[src]];
            idx[src]++;
        }
        return r;
    }
    
    static private double[] mergeDoubleArrays(int[] n, ValueArray...inputValueArray) {
        int[] idx = new int[inputValueArray.length];
        double [] r = new double[n.length];
        for(int i= 0; i<n.length; i++) {
            int src = n[i];
            r[i] = ((double[])inputValueArray[src].obj)[idx[src]];
            idx[src]++;
        }
        return r;
    }
    
    
    static private float[] mergeFloatArrays(int[] n, ValueArray...inputValueArray) {
        int[] idx = new int[inputValueArray.length];
        float [] r = new float[n.length];
        for(int i= 0; i<n.length; i++) {
            int src = n[i];
            r[i] = ((float[])inputValueArray[src].obj)[idx[src]];
            idx[src]++;
        }
        return r;
    }
    
    static private Object[] mergeObjectArrays(int[] n, ValueArray...inputValueArray) {
        int[] idx = new int[inputValueArray.length];
        Object [] r = new Object[n.length];
        for(int i= 0; i<n.length; i++) {
            int src = n[i];
            r[i] = ((Object[])inputValueArray[src].obj)[idx[src]];
            idx[src]++;
        }
        return r;
    }
    
    static private BitSet mergeBitsets(int[] n, ValueArray...inputValueArray) {
        int[] idx = new int[inputValueArray.length];
        BitSet r = new BitSet(n.length);
        for(int i= 0; i<n.length; i++) {
            int src = n[i];
            boolean b =((BitSet)inputValueArray[src].obj).get(idx[src]); 
            r.set(i, b);
            idx[src]++;
        }
        return r;
    }
}
