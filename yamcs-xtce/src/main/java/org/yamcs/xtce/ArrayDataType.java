package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * An array of values of the type referenced in {@link #type} and have the number of array dimensions as specified in
 * {@link #numberOfDimensions}
 * 
 * @author nm
 *
 */
public class ArrayDataType extends NameDescription {
    private static final long serialVersionUID = 1L;

    private DataType type;
    private int numberOfDimensions;

    public ArrayDataType(String name) {
        super(name);
    }

    /**
     * Sets the type of the elements of the array
     * 
     * @param type
     */
    public void setType(DataType type) {
        this.type = type;
    }

    /**
     * returns the type of the elements of the array
     * 
     * @param type
     * @return
     */
    public DataType getType() {
        return type;
    }

    public void setNumberOfDimensions(int numberOfDimensions) {
        this.numberOfDimensions = numberOfDimensions;
    }

    public int getNumberOfDimensions() {
        return numberOfDimensions;
    }

    public Type getValueType() {
        return Type.ARRAY;
    }
}
