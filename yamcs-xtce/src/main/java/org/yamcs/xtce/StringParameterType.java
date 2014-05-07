package org.yamcs.xtce;

import java.util.Set;

public class StringParameterType extends StringDataType implements ParameterType {
    private static final long serialVersionUID = 1L;

    public StringParameterType(String name) {
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
        return "string";
    }
    

    @Override
    public String toString() {
        return "StringParameterType name:"+name+" encoding:"+encoding;
    }

}
