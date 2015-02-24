package org.yamcs.xtce;


public class IntegerArgumentType extends IntegerDataType implements ArgumentType {
	private static final long serialVersionUID=1L;

	
	public IntegerArgumentType(String name){
		super(name);
	}
			
	@Override
    public String toString() {
		return "IntegerDataType name:"+name+" sizeInBits:"+sizeInBits+" signed:"+signed+" encoding:"+encoding;
	}

    @Override
    public String getTypeAsString() {
        return "integer";
    }
}
