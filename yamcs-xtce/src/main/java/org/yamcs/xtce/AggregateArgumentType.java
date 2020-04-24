package org.yamcs.xtce;

import java.util.List;

public class AggregateArgumentType extends AggregateDataType implements ArgumentType {
    private static final long serialVersionUID = 2L;
    
    public AggregateArgumentType(String name) {
        super(name);
    }

    public AggregateArgumentType(AggregateArgumentType t) {
        super(t);
    }


    @Override
    public List<UnitType> getUnitSet() {
        return null;
    }

    @Override
    public AggregateArgumentType copy() {
        return new AggregateArgumentType(this);
    }

}
