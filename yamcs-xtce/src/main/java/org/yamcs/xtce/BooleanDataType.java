package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class BooleanDataType extends BaseDataType {
    private static final long serialVersionUID = 1L;
    Boolean initialValue;
   
    String oneStringValue = "True";
    String zeroStringValue = "False";
   
    protected BooleanDataType(Builder<?> builder) {
        super(builder);
        
        if(builder.oneStringValue != null) {
            this.oneStringValue = builder.oneStringValue;
        }
        if(builder.zeroStringValue != null) {
            this.zeroStringValue = builder.zeroStringValue;
        }
        if (builder.baseType != null && builder.baseType instanceof BooleanDataType) {
            BooleanDataType baseType = (BooleanDataType) builder.baseType;
            if(builder.oneStringValue == null && baseType.oneStringValue != null ) {
                this.oneStringValue = baseType.oneStringValue;
            }
            if(builder.zeroStringValue == null && baseType.zeroStringValue != null ) {
                this.zeroStringValue = baseType.zeroStringValue;
            }
        }
        
        setInitialValue(builder);
    }
    
    protected BooleanDataType(BooleanDataType t) {
        super(t);
    }

    protected void setInitialValue(Object initialValue) {
        if(initialValue instanceof Boolean) {
            this.initialValue = (Boolean) initialValue;
        } else if (initialValue instanceof String) {
            this.initialValue = parseString((String)initialValue);
        } else {
            throw new IllegalArgumentException("Unsupported type for initial value "+initialValue.getClass());
        }
    }
    
    public Boolean getInitialValue() {
        return initialValue;
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

    public String getZeroStringValue() {
        return zeroStringValue;
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

    public static abstract class Builder <T extends Builder<T>> extends BaseDataType.Builder<T> {
        String oneStringValue;
        String zeroStringValue;
        
        public Builder() {
        }
        
        public Builder(BooleanDataType dataType) {
            super(dataType);
            this.oneStringValue = dataType.oneStringValue;
            this.zeroStringValue = dataType.zeroStringValue;
        }
        
        public void setOneStringValue(String oneStringValue) {
            this.oneStringValue = oneStringValue;
        }

        public void setZeroStringValue(String zeroStringValue) {
            this.zeroStringValue = zeroStringValue;
        }
    }
}
