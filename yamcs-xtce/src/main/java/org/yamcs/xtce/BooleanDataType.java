package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class BooleanDataType extends BaseDataType {
    private static final long serialVersionUID = 1L;
   
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
        
        if (builder.initialValue != null) {
            if(builder.initialValue instanceof Boolean) {
                this.initialValue = builder.initialValue;
            } else if (builder.initialValue instanceof String) {
                this.initialValue = parseString((String)builder.initialValue);
            } else {
                throw new IllegalArgumentException("Unsupported type for initial value "+builder.initialValue.getClass());
            }
        }
    }
    
    protected BooleanDataType(BooleanDataType t) {
        super(t);
    }

    public Boolean getInitialValue() {
        return (Boolean)initialValue;
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
