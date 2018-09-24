package org.yamcs.xtce;

/**
 * Describe an array parameter type. 
 * The size and number of dimensions are described here. See ArrayParameterRefEntryType, NameReferenceType and ArrayDataType.
 * 
 * @author nm
 *
 */
public class ArrayParameterType extends ArrayDataType implements ParameterType {
    
    public ArrayParameterType(String name) {
        super(name);
    }

    public ArrayParameterType(ArrayParameterType t) {
        super(t);
    }

    private static final long serialVersionUID = 1L;

    @Override
    public String getTypeAsString() {
        return null;
    }

    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public Object parseString(String stringValue) {
        // TODO Auto-generated method stub
        return null;
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
    public void setInitialValue(String initialValue) {
    }

    

}
