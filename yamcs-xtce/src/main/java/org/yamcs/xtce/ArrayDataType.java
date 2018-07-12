package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * An array of values of the type referenced in {@link #type} and have the number of array dimensions as specified in
 * {@link #numberOfDimensions}
 * 
 * @author nm
 *
 */
public class ArrayDataType extends NameDescription implements DataType {
    private static final long serialVersionUID = 1L;

    private DataType type;
    private int numberOfDimensions;

    public ArrayDataType(String name) {
        super(name);
    }

    public ArrayDataType(ArrayDataType t) {
        super(t);
        this.type = t.type;
        this.numberOfDimensions = t.numberOfDimensions;
    }

    /**
     * Sets the type of the elements of the array
     * 
     * @param type
     */
    public void setElementType(DataType type) {
        this.type = type;
    }

    /**
     * returns the type of the elements of the array
     *
     * @return - the type of the elements of the array
     */
    public DataType getElementType() {
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

    @Override
    public String getTypeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getName());
        for (int i = 0; i < numberOfDimensions; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    @Override
    public void setInitialValue(String initialValue) {

    }
}
