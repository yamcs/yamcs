package org.yamcs.xtce;

public class BooleanParameterType extends BooleanDataType implements ParameterType {

    private static final long serialVersionUID = 1L;

    public BooleanParameterType(String name) {
        super(name);
    }

    /**
     * Creates a shallow copy of the parameter type
     * 
     */
    public BooleanParameterType(BooleanParameterType t) {
        super(t);
    }
    
    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public ParameterType copy() {
        return new BooleanParameterType(this);
    }
}
