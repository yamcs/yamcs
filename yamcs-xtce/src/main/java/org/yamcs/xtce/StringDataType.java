package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class StringDataType extends BaseDataType {
    private static final long serialVersionUID = 2L;

    /**
     * For telemetry, specify as UTF-8 or UTF-16 value to match the encoding. 
     * This range check will be applied before conversion to the host string data type.  
     * For commanding, the range check occurs on the string host data type encoding, 
     * whatever that is -- before injection on the command link.
     */
    IntegerRange sizeRangeInCharacters; 
    
    protected StringDataType(Builder<?> builder) {
        super(builder);
        this.sizeRangeInCharacters = builder.sizeRangeInCharacters;
        if (builder.initialValue != null) {
            this.initialValue = builder.initialValue;
        }
    }
    
    protected StringDataType(StringDataType t) {
        super(t);
        this.sizeRangeInCharacters = t.sizeRangeInCharacters;
    }


    public String getInitialValue() {
        return (String)initialValue;
    }


    public IntegerRange getSizeRangeInCharacters() {
        return sizeRangeInCharacters;
    }

    @Override
    public Object parseString(String stringValue) {       
        return stringValue;
    }

    @Override
    public Type getValueType() {
        return Type.STRING;
    }
    

    @Override
    public String getTypeAsString() {
        return "string";
    }
    
    
    public static abstract class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {
        IntegerRange sizeRangeInCharacters; 
        public Builder() {
        }
        
        public Builder(StringDataType stringType) {
            super(stringType);
            this.sizeRangeInCharacters = stringType.sizeRangeInCharacters;
        }
    }
}
