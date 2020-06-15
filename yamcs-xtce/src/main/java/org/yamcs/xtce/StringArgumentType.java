package org.yamcs.xtce;


public class StringArgumentType extends StringDataType implements ArgumentType {
    private static final long serialVersionUID = 2L;

    public StringArgumentType(Builder builder) {
        super(builder);
    }
    
    /**
     * Creates a shallow copy of the parameter type, giving it a new name. 
     */
    public StringArgumentType(StringArgumentType t) {
        super(t);
    }
    
    
    @Override
    public String getTypeAsString() {
        return "string";
    }
    

    @Override
    public String toString() {
    	StringBuilder sb = new StringBuilder();
    	sb.append("StringArgumentType name:").append(name);
    	if(initialValue!=null) sb.append("defValue: ").append(initialValue);
    	if(sizeRangeInCharacters!=null) sb.append(" sizeRange: ").append(sizeRangeInCharacters);
    	
    	sb.append(" encoding: ").append(encoding);
    	return sb.toString();
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder extends StringDataType.Builder<Builder> implements ArgumentType.Builder<Builder> {
        public Builder() {
        }

        public Builder(StringArgumentType stringArgumentType) {
           super(stringArgumentType);
        }

        @Override
        public StringArgumentType build() {
            return new StringArgumentType(this);
        }
    }
}
