package org.yamcs.xtce;


public class StringParameterType extends StringDataType implements ParameterType {
    private static final long serialVersionUID = 1L;

    public StringParameterType(String name) {
        super(name);
    }
    /**
     * Creates a shallow copy of the parameter type, giving it a new name. 
     */
    public StringParameterType(StringParameterType t) {
        super(t);
    }

    
    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public String toString() {
        return "StringParameterType name:"+name+" encoding:"+encoding;
    }

    @Override
    public ParameterType copy() {
        return new StringParameterType(this);
    }
}
