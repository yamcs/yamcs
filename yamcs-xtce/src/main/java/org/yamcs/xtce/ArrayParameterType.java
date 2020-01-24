package org.yamcs.xtce;

import java.util.List;

/**
 * Describe an array parameter type. The size and number of dimensions are described here. See
 * {@link ArrayParameterEntry}, NameReferenceType and ArrayDataType.
 * <p>
 * Note: XTCE 1.1 defines only the number of dimensions (integer) as part of the ArrayDataType and leave the dimensions
 * list to be defined as part of the {@link ArrayParameterEntry}
 * <p>
 * In XTCE 1.2 the dimensions list is also defined in this class and can be optionally omitted from the
 * {@link ArrayParameterEntry}.
 * <p>
 * We support both behaviours.
 * 
 * @author nm
 *
 */
public class ArrayParameterType extends ArrayDataType implements ParameterType {
    private static final long serialVersionUID = 1L;
    List<IntegerValue> dim;

    public ArrayParameterType(String name) {
        super(name, -1);
    }

    public ArrayParameterType(String name, int numberOfDimensions) {
        super(name, numberOfDimensions);
        if(numberOfDimensions<0) {
            throw new IllegalArgumentException("numberOfDimensions should be positive");
        }
    }

    public ArrayParameterType(ArrayParameterType t) {
        super(t);
        this.dim = t.dim;
    }

    @Override
    public String getTypeAsString() {
        return getElementType().getTypeAsString() + "[]";
    }

    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public Object parseStringForRawValue(String stringValue) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setEncoding(DataEncoding dataEncoding) {
        throw new UnsupportedOperationException("aggregate parameters do not support encodings");
    }

    @Override
    public ArrayParameterType copy() {
        return new ArrayParameterType(this);
    }

    @Override
    public DataEncoding getEncoding() {
        throw new UnsupportedOperationException("aggregate parameters do not support encodings");
    }

    public void setSize(List<IntegerValue> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Dimension sizes cannot be empty");
        }
        this.dim = list;
        setNumberOfDimensions(dim.size());
    }

    /**
     * Return the dimension list (defined as from XTCE 1.2). The list here is not really used except for populating the
     * {@link ArrayParameterEntry#dim} at the MDB load.
     * 
     * @return
     */
    public List<IntegerValue> getSize() {
        return dim;
    }
}
