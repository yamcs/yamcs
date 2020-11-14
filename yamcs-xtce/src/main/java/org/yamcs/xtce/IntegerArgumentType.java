package org.yamcs.xtce;

public class IntegerArgumentType extends IntegerDataType implements ArgumentType {
    private static final long serialVersionUID = 3L;

    public IntegerArgumentType(Builder builder) {
        super(builder);
    }

    /**
     * Creates a shallow copy of the parameter type, giving it a new name.
     */
    public IntegerArgumentType(IntegerArgumentType t) {
        super(t);
    }

    @Override
    public String getTypeAsString() {
        return "integer";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IntegerArgumentType name:").append(name)
                .append(" sizeInBits:").append(sizeInBits)
                .append(" signed: ").append(signed);

        if (initialValue != null)
            sb.append(", defaultValue: ").append(initialValue);
        if (validRange != null)
            sb.append(", validRange: ").append(validRange.toString(isSigned()));

        sb.append(", encoding: ").append(encoding);

        return sb.toString();
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder extends IntegerDataType.Builder<Builder> implements ArgumentType.Builder<Builder> {
        public Builder(IntegerArgumentType integerArgumentType) {
            super(integerArgumentType);
        }
        public Builder() {
        }
        
        @Override
        public IntegerArgumentType build() {
            return new IntegerArgumentType(this);
        }

    }
}
