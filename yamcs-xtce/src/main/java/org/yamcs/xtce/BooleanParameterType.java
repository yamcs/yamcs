package org.yamcs.xtce;

import java.util.Set;

public class BooleanParameterType extends BooleanDataType implements ParameterType {

    private static final long serialVersionUID = 1L;

    public BooleanParameterType(String name) {
        super(name);
    }
    
    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        return null;
    }

    @Override
    public String getTypeAsString() {
        return "boolean";
    }
}
