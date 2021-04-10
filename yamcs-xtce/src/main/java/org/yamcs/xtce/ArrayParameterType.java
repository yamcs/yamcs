package org.yamcs.xtce;

/**
 * Describe an array parameter type. The size and number of dimensions are described here. See
 * {@link ArrayParameterEntry}, NameReferenceType and ArrayDataType.
 * <p>
 * Note: XTCE 1.1 defines only the number of dimensions (integer) as part of the ArrayDataType and leaves the dimension
 * list (containing the size of each dimension) to be defined as part of the {@link ArrayParameterEntry}
 * <p>
 * In XTCE 1.2 the dimension list is also defined in this class and can be optionally omitted from the
 * {@link ArrayParameterEntry}.
 * <p>
 * We support both behaviours.
 * 
 * @author nm
 *
 */
public class ArrayParameterType extends ArrayDataType implements ParameterType {
    private static final long serialVersionUID = 1L;

    public ArrayParameterType(Builder builder) {
        super(builder);
    }
    
    public ArrayParameterType(String name) {
        super(name, -1);
    }

    public ArrayParameterType(String name, int numberOfDimensions) {
        super(name, numberOfDimensions);
        if (numberOfDimensions < 0) {
            throw new IllegalArgumentException("numberOfDimensions should be positive");
        }
    }

    public ArrayParameterType(ArrayParameterType t) {
        super(t);
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
    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public DataEncoding getEncoding() {
        throw new UnsupportedOperationException("array parameters do not support encodings");
    }

   


    @Override
    public String toString() {
        return "ArrayParameterType name:" + name + " numberOfDimensions:" + getNumberOfDimensions();
    }
    
    public static class Builder extends ArrayDataType.Builder<Builder> implements ParameterType.Builder<Builder> {

        public Builder() {
            
        }
        
        public Builder(ArrayParameterType arrayParameterType) {
           super(arrayParameterType);
        }

        @Override
        public ArrayParameterType build() {
            return new ArrayParameterType(this);
        }

        @Override
        public Builder setEncoding(DataEncoding.Builder<?> dataEncoding) {
            throw new UnsupportedOperationException("array parameters do not support encodings");
        }
        
    }
}
