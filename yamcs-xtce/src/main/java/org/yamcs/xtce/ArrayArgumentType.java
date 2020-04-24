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
    
    public ArrayArgumentType(String name, int numberOfDimensions) {
        super(name, numberOfDimensions);
    }

    public ArrayArgumentType(ArrayArgumentType t) {
        super(t);
    }

    @Override
    public String getTypeAsString() {
        return null;
    }

    @Override
    public void setInitialValue(String initialValue) {
    }

    @Override
    public List<UnitType> getUnitSet() {
        return null;
    }
    
    
    @Override
    public ArrayArgumentType copy() {
        return new ArrayArgumentType(this);
    }

}
