package org.yamcs.xtce;

import java.util.Collections;
import java.util.List;

public class AbsoluteTimeArgumentType extends AbsoluteTimeDataType implements ArgumentType {
    private static final long serialVersionUID = 1L;

    public AbsoluteTimeArgumentType(Builder builder) {
        super(builder);
    }
    
    public AbsoluteTimeArgumentType(String name) {
        super(name);
    }

    /**
     * Copy constructor
     */
    public AbsoluteTimeArgumentType(AbsoluteTimeArgumentType t) {
        super(t);
    }


    @Override
    public List<UnitType> getUnitSet() {
        return Collections.emptyList();
    }


    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }
    
    @Override
    public String toString() {
        return "AbsoluteTimeArgumentType name:" + name
                + ((getReferenceTime() != null) ? ", referenceTime:" + getReferenceTime() : "");
    }
   
    
    public static class Builder extends AbsoluteTimeDataType.Builder<Builder> implements ArgumentType.Builder<Builder>{

        public Builder() {
        }

        public Builder(AbsoluteTimeArgumentType absoluteTimeArgumentType) {
            super(absoluteTimeArgumentType);
        }

        @Override
        public AbsoluteTimeArgumentType build() {
            return new AbsoluteTimeArgumentType(this);
        }
    }
}
