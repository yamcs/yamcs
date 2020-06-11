package org.yamcs.xtce;

import java.util.List;

/**
 * Describe an array parameter type. 
 * The size and number of dimensions are described here. See ArrayParameterRefEntryType, NameReferenceType and ArrayDataType.
 * 
 * @author nm
 *
 */
public class ArrayArgumentType extends ArrayDataType implements ArgumentType {
    private static final long serialVersionUID = 2L;
    
    ArrayArgumentType(Builder builder) {
        super(builder);
    }
    
    public ArrayArgumentType(String name, int numberOfDimensions) {
        super(name, numberOfDimensions);
    }
    
    public ArrayArgumentType(String name) {
        super(name, -1);
    }
    
    public ArrayArgumentType(ArrayArgumentType t) {
        super(t);
    }

    @Override
    public String getTypeAsString() {
        return null;
    }

    @Override
    public List<UnitType> getUnitSet() {
        return null;
    }
    
    
    @Override
    public ArrayArgumentType copy() {
        return new ArrayArgumentType(this);
    }
    
    public static class Builder extends ArrayDataType.Builder<Builder> {
        @Override
        public ArrayArgumentType build() {
            return new ArrayArgumentType(this);
        }
    }

}
