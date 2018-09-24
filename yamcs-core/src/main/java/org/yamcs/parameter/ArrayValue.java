package org.yamcs.parameter;

import java.util.Arrays;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * Multidimensional value array. All the elements of the array have to have the same type.
 * 
 * The number of dimensions and the size of each dimension are fixed in the constructor.
 * 
 * The array is internally stored into a flat java array. The {@link #flatIndex(int[])} can be used to convert from the
 * multi dimensional index to the flat index.
 * 
 * @author nm
 *
 */
public class ArrayValue extends Value {
    final Value[] elements;
    final int[] dim;
    final Type elementType;

    /**
     * Create a new value array of size dim[0]*dim[1]*...*dim[n]
     * 
     * @param dim
     * @param elementType
     */
    public ArrayValue(int[] dim, Type elementType) {
        if (dim.length == 0) {
            throw new IllegalArgumentException("The array has to be at least ");
        }
        this.elementType = elementType;
        int fs = flatSize(dim);
        this.dim = dim;
        this.elements = new Value[fs];
    }

    @Override
    public Type getType() {
        return Type.ARRAY;
    }

    /**
     * Get the value of the element at the given index
     * 
     * @param idx
     *            - multidimensional index
     * @return - the value
     * 
     * @throws ArrayIndexOutOfBoundsException
     *             if the index is outside of the array
     */
    public Value getElementValue(int[] idx) {
        if (dim.length != idx.length) {
            throw new IllegalArgumentException("number of dimensions should be " + dim.length);
        }

        return elements[flatIndex(idx)];
    }

    /**
     * Sets the element at the given index.
     * 
     * @param idx
     *            - multidimensional index
     * @param v
     *            - the value to be set
     * 
     * @throws ArrayIndexOutOfBoundsException
     *             if the index is outside of the array
     * @throws IllegalArgumentException
     *             if the number of dimensions (idx.lenght) does not match with the array number of dimensions or if the
     *             element type does not match with the array element type
     */
    public void setElementValue(int[] idx, Value v) {

        if (dim.length != idx.length) {
            throw new IllegalArgumentException("number of dimensions should be " + dim.length);
        }
        if (v.getType() != elementType) {
            throw new IllegalArgumentException("Element type should be "+elementType);
        }
        elements[flatIndex(idx)] = v;
    }

    public int flatIndex(int[] idx) {
        if (idx.length == 1) {
            return idx[0];
        }

        int n = idx[0];
        for (int i = 1; i < dim.length; i++) {
            n = n * dim[i] + idx[i];
        }
        return n;
    }

    static public int flatSize(int[] dim) {
        if (dim.length == 1) {
            return dim[0];
        }

        int n = dim[0];

        for (int i = 1; i < dim.length; i++) {
            n *= dim[i];
        }
        return n;
    }

    /**
     * Set the value of an element using the flat index
     * 
     * @param flatIdx
     *            - flat index of the element to be set
     * @param v
     *            - the value to be set
     * @throws ArrayIndexOutOfBoundsException
     *             if the index is outside of the array
     */
    public void setElementValue(int flatIdx, Value v) {
        elements[flatIdx] = v;
    }

    /**
     * Get the element value using the flat index;
     * 
     * @param flatIdx
     *            - flat index of the element to be set
     * @return the value
     */
    public Value getElementValue(int flatIdx) {
        return elements[flatIdx];
    }

    public String toString() {
        return Arrays.toString(elements);
    }

    /**
     * Return the length of the flat array
     * This is the product of the size of the individual dimensions.
     * 
     * @return
     */
    public int flatLength() {
        return elements.length;
    }
    
    /**
     * 
     * @return the type of the array elements
     */
    public Type getElementType() {
        return elementType;
    }

    /**
     * returns the dimensions of the array
     * 
     * @return
     */
    public int[] getDimensions() {
        return dim;
    }
}
