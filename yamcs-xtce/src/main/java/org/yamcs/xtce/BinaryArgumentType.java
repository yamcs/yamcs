package org.yamcs.xtce;

public class BinaryArgumentType extends BinaryDataType implements ArgumentType {
	private static final long serialVersionUID = 1L;
	public BinaryArgumentType(String name){
		super(name);
	}
	
    @Override
    public String getTypeAsString() {
        return "binary";
    }

    @Override
    public String toString() {
        return "BinaryArgumentType name:"+name+" encoding:"+encoding;
    }

}
