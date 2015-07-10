package org.yamcs.xtce;

public class IntegerDataType extends NumericDataType {
    private static final long serialVersionUID = 200706051146L;
    int sizeInBits=32;
    protected boolean signed=true;

    /**
     * XTCE: The Valid Range bounds the universe of possible values this Parameter may have. 
     *        For Telemetry the valid range is always applied before calibration, regardless of the value of validRangeAppliesToCalibrated. 
     *        For commanding, if validRangeAppliesToCalibrated is false -- it is applied before calibration to the link DataEncoding.
     */
    IntegerValidRange validRange;

    /**
     * Used mainly for command arguments to specify the default value
     */
    String initialValue;


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

    /**
     * returns the range for the values of this type to be valid or null if there is no range set (meaning that all values are valid)
     * @return
     */
    public IntegerValidRange getValidRange() {
        return validRange;
    }

    public void setValidRange(IntegerValidRange range) {
        this.validRange = range;		
    }

    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
    }

    public String getInitialValue() {
        return initialValue;
    }

    @Override
    public Object parseString(String stringValue) {
        if(sizeInBits>32) {
            return Long.decode(stringValue);
        } else {
            return Long.decode(stringValue).intValue();
        }
    }
}
