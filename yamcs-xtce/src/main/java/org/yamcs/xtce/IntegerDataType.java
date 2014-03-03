package org.yamcs.xtce;

public class IntegerDataType extends NumericDataType {
	private static final long serialVersionUID = 200706051146L;
	int sizeInBits=32;
	boolean signed=true;
	
	IntegerDataType(String name){
		super(name);
	}

	public void setSigned(boolean signed) {
	    this.signed=signed;
	}
	
	public boolean isSigned() {
		return signed;
	}

	public int getSizeInBits() {
		return sizeInBits;
	}

	public void setSizeInBits(int sizeInBits) {
	    this.sizeInBits=sizeInBits;
	}
}
