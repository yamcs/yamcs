package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public abstract class FloatDataType extends NumericDataType {
    private static final long serialVersionUID = 1L;
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


    int sizeInBits = 32;
    
    protected FloatDataType(String name) {
        super(name);
    }
    
    protected FloatDataType(FloatDataType t) {
        super(t);
        this.initialValue = t.initialValue;
        this.validRange = t.validRange;
        this.sizeInBits = t.sizeInBits;
    }
    

    public Double getInitialValue() {
        return initialValue;
    }
    public void setInitialValue(double initialValue) {
        this.initialValue = initialValue;
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

    @Override
    public void setInitialValue(String initialValue) {
        this.initialValue = Double.parseDouble(initialValue);
    }

    @Override
    public Type getValueType() {
        return (sizeInBits<=32)?Type.FLOAT:Type.DOUBLE;
    }
    
    @Override
    public String getTypeAsString() {
        return "float";
    }
}
