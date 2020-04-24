package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class BooleanDataType extends BaseDataType {
    private static final long serialVersionUID = 1L;
   
    String oneStringValue = "True";
    String zeroStringValue = "False";
   
    protected BooleanDataType(BooleanDataType t) {
        super(t);
    }

    public Boolean getInitialValue() {
        return (Boolean)initialValue;
    }

    public void setInitialValue(Boolean initialValue) {
        this.initialValue = initialValue;
    }

    BooleanDataType(String name) {
        super(name);
    }

   
    /**
     * Returns {@link Boolean#parseBoolean(String)}
    */
    public Boolean parseString(String stringValue) {
        if(oneStringValue.equalsIgnoreCase(stringValue)) {
            return Boolean.TRUE;
        } else if (zeroStringValue.equalsIgnoreCase(stringValue)) {
            return Boolean.FALSE;
        } else {
            throw new IllegalArgumentException("Invalid initialValue, should be '"+oneStringValue+"' or '"+zeroStringValue+"'");
        }
    }

   
    public String getOneStringValue() {
        return oneStringValue;
    }

    public void setOneStringValue(String oneStringValue) {
        this.oneStringValue = oneStringValue;
    }

    public String getZeroStringValue() {
        return zeroStringValue;
    }

    public void setZeroStringValue(String zeroStringValue) {
        this.zeroStringValue = zeroStringValue;
    }

    @Override
    public Type getValueType() {
        return Type.BOOLEAN;
    }

    @Override
    public String getTypeAsString() {
        return "boolean";
    }
    
    @Override
    public String toString(){ 
        return "BooleanData encoding: " + encoding;
    }

}
