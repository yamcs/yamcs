package org.yamcs.xtce;

public class BinaryDataType extends BaseDataType {
    private static final long serialVersionUID = 1L;
    
    /**
     * DIFFERS_FROM_XTCE
     */
    IntegerRange sizeRangeInBytes; 
    BinaryDataType(String name) {
		super(name);
	}
    
	public IntegerRange getSizeRangeInBytes() {
		return sizeRangeInBytes;
	}


	public void setSizeRangeInBytes(IntegerRange sizeRangeInBytes) {
		this.sizeRangeInBytes = sizeRangeInBytes;
	}
    
	@Override
    public String toString(){ 
		return "BinaryData encoding: "+encoding;
	}
}
