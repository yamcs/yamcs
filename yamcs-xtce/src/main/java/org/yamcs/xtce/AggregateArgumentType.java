package org.yamcs.xtce;

import java.util.List;

public class AggregateArgumentType extends AggregateDataType implements ArgumentType {
    public AggregateArgumentType(String name) {
        super(name);
    }

    private static final long serialVersionUID = 1L;

    @Override
    public String getTypeAsString() {
        return null;
    }


    @Override
    public List<UnitType> getUnitSet() {
        return null;
    }


    @Override
    public void setInitialValue(String initialValue) {
        // TODO Auto-generated method stub
        
    }
}
