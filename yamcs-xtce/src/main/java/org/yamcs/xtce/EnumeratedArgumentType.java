package org.yamcs.xtce;


public class EnumeratedArgumentType extends EnumeratedDataType implements ArgumentType {
	private static final long serialVersionUID = 1;
	
	
	public EnumeratedArgumentType(String name) {
		super(name);
	}
	
	public long decalibrate(String label) {
		for(ValueEnumeration ve: enumerationList) {
			if(ve.getLabel().equals(label)) {
				return ve.getValue();
			}
		}
		return 0;
	}

	public String getCalibrationDescription() {
		return "EnumeratedArgumentType: "+enumeration;
	}

    @Override
    public String getTypeAsString() {
        return "enumeration";
    }
    
	@Override
    public String toString() {
        return "EnumeratedArgumentType: "+enumeration+" encoding:"+encoding;
    }

}
