package org.yamcs.xtce;

public class BooleanArgumentType extends BooleanDataType implements ArgumentType {
    private static final long serialVersionUID = 3L;

    public BooleanArgumentType(Builder builder) {
        super(builder);
    }
    /**
     * Creates a shallow copy of the parameter type
     * 
     */
    public BooleanArgumentType(BooleanArgumentType t) {
        super(t);
    }

    @Override
    public String getTypeAsString() {
        return "boolean";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BooleanArgumentType name:").append(name);

        if (initialValue != null)
            sb.append(", defaultValue: ").append(initialValue);
        sb.append(", encoding: ").append(encoding);

        return sb.toString();
    }

    @Override
    public BooleanArgumentType.Builder toBuilder() {
        return new Builder(this);
    }
    
    
    public static class Builder extends BooleanDataType.Builder<Builder> implements ArgumentType.Builder<Builder> {
        public Builder() {
        }
        
        public Builder(BooleanArgumentType booleanArgumentType) {
           super(booleanArgumentType);
        }

        @Override
        public ArgumentType build() {
            return new BooleanArgumentType(this);
        }
    }
}
