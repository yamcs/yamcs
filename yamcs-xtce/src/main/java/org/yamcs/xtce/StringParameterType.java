package org.yamcs.xtce;


public class StringParameterType extends StringDataType implements ParameterType {
    private static final long serialVersionUID = 1L;


    public StringParameterType(Builder builder) {
        super(builder);
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
    public Builder toBuilder() {
        return new Builder(this);
    }
    
    @Override
    public String toString() {
        return "StringParameterType name:"+name+" encoding:"+encoding;
    }
    
    public static class Builder extends StringDataType.Builder<Builder> implements ParameterType.Builder<Builder> {

        public Builder() {
        }
        
        public Builder(StringParameterType stringParameterType) {
            super(stringParameterType);
        }

        @Override
        public StringParameterType build() {
            return new StringParameterType(this);
        }
        
    }

}
