package org.yamcs.xtce;

public class BinaryDataType extends BaseDataType {
    private static final long serialVersionUID = 1L;
    
    /**
     * DIFFERS_FROM_XTCE XTCE does not define a size range
     */
    IntegerRange sizeRangeInBytes; 
    
    byte[] initialValue;
    

	BinaryDataType(String name) {
		super(name);
	}
	
    public byte[] getInitialValue() {
		return initialValue;
	}

	public void setInitialValue(byte[] initialValue) {
		this.initialValue = initialValue;
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
