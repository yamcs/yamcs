package org.yamcs.xtce;

public class StringArgumentType extends StringDataType implements ArgumentType {
    private static final long serialVersionUID = 2L;

    public StringArgumentType(String name) {
        super(name);
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

}
