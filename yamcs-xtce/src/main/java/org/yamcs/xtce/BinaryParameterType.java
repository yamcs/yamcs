package org.yamcs.xtce;

import java.util.Set;

public class BinaryParameterType extends BinaryDataType implements ParameterType {
    private static final long serialVersionUID = 200805131551L;
    public BinaryParameterType(String name){
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
        return "binary";
    }
}
