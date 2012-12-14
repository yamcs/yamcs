package org.yamcs.xtce;

public class BinaryDataType extends BaseDataType {
    private static final long serialVersionUID = 201010111059L;
    BinaryDataType(String name) {
		super(name);
	}
    
	@Override
    public String toString(){ 
		return "BinaryData encoding: "+encoding;
	}
}
