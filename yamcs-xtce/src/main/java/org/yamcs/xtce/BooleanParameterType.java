package org.yamcs.xtce;

public class BooleanParameterType extends BooleanDataType implements ParameterType {

    private static final long serialVersionUID = 1L;

    public BooleanParameterType(Builder builder) {
        super(builder);
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
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder extends BooleanDataType.Builder<Builder> implements ParameterType.Builder<Builder> {
        public Builder() {

        }

        public Builder(BooleanParameterType booleanParameterType) {
            super(booleanParameterType);
        }

        @Override
        public BooleanParameterType build() {
            return new BooleanParameterType(this);
        }
    }
}
