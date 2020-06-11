package org.yamcs.xtce;

public class BinaryParameterType extends BinaryDataType implements ParameterType {
    private static final long serialVersionUID = 200805131551L;

    BinaryParameterType(Builder builder) {
        super(builder);
    }

    public BinaryParameterType(BinaryParameterType t1) {
        super(t1);
    }

    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public String getTypeAsString() {
        return "binary";
    }

    @Override
    public Builder toBuilder() {
        return new BinaryParameterType.Builder(this);
    }

    public static class Builder extends BinaryDataType.Builder<Builder> implements ParameterType.Builder<Builder> {
        public Builder() {
        }

        public Builder(BinaryParameterType binaryParameterType) {
            super(binaryParameterType);
        }

        @Override
        public BinaryParameterType build() {
            return new BinaryParameterType(this);
        }
    }
}
