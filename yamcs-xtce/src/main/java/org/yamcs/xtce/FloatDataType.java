package org.yamcs.xtce;

public class FloatDataType extends NumericDataType {
	private static final long serialVersionUID = 200706061220L;
	
	int sizeInBits=32;
	FloatDataType(String name) {
		super(name);
	}
	public int getSizeInBits() {
		return sizeInBits;
	}
	public void setSizeInBits(int sizeInBits) {
        this.sizeInBits=sizeInBits;
    }
}
