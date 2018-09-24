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
    
    public ArrayArgumentType(String name) {
        super(name);
    }

    private static final long serialVersionUID = 1L;

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

}
