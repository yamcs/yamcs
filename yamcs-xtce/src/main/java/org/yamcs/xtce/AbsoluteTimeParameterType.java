package org.yamcs.xtce;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AbsoluteTimeParameterType extends AbsoluteTimeDataType implements ParameterType {
    private static final long serialVersionUID = 1L;

    public AbsoluteTimeParameterType(Builder builder) {
        super(builder);
    }
    
    public AbsoluteTimeParameterType(String name) {
        super(name);
    }

    /**
     * Creates a shallow copy of the parameter type, giving it a new name.
     */
    public AbsoluteTimeParameterType(AbsoluteTimeParameterType t) {
        super(t);
    }


    @Override
    public List<UnitType> getUnitSet() {
        return Collections.emptyList();
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        if (referenceTime == null || referenceTime.getOffsetFrom()==null) {
            return Collections.emptySet();
        }
        ParameterInstanceRef pref = referenceTime.getOffsetFrom();
        return Collections.singleton(pref.getParameter());
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
    public String toString() {
        return "AbsoluteTimeParameterType name:" + name
                + ((getReferenceTime() != null) ? ", referenceTime:" + getReferenceTime() : "");
    }
   
    
    public static class Builder extends AbsoluteTimeDataType.Builder<Builder> implements ParameterType.Builder<Builder>{

        public Builder() {
        }

        public Builder(AbsoluteTimeParameterType absoluteTimeParameterType) {
            super(absoluteTimeParameterType);
        }

        @Override
        public AbsoluteTimeParameterType build() {
            return new AbsoluteTimeParameterType(this);
        }

        
    }
}
