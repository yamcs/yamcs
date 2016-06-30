package org.yamcs.xtce;

public class FloatDataType extends NumericDataType {
    private static final long serialVersionUID = 200706061220L;
    /**
     * XTCE: The Valid Range bounds the universe of possible values this Parameter may have. For Telemetry the valid range is always 
     * applied before calibration, regardless of the value of validRangeAppliesToCalibrated. For commanding, 
     * if validRangeAppliesToCalibrated is false -- it is applied before calibration to the link DataEncoding.
     * 
     */
    FloatValidRange validRange;

    /**
     * XTCE: Initial value is always given in calibrated form
     */
    Double initialValue;

    public Double getInitialValue() {
        return initialValue;
    }
    public void setInitialValue(double initialValue) {
        this.initialValue = initialValue;
    }

    int sizeInBits=32;
    FloatDataType(String name) {
        super(name);
    }
    public int getSizeInBits() {
        return sizeInBits;
    }
    public void setSizeInBits(int sizeInBits) {
        this.sizeInBits = sizeInBits;
    }

    public void setValidRange(FloatValidRange validRange) {
        this.validRange = validRange;
    }

    public FloatValidRange getValidRange() {
        return validRange;
    }
    @Override
    public Object parseString(String stringValue) {
        if(sizeInBits==32) {
            return Float.parseFloat(stringValue);
        } else {
            return Double.parseDouble(stringValue);
        }
    }
}
