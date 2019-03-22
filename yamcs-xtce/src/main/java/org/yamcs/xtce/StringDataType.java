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

    protected StringDataType(String name) {
        super(name);
    }
    
    protected StringDataType(StringDataType t) {
        super(t);
        this.sizeRangeInCharacters = t.sizeRangeInCharacters;
    }


    public String getInitialValue() {
        return (String)initialValue;
    }

    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
    }



    public IntegerRange getSizeRangeInCharacters() {
        return sizeRangeInCharacters;
    }


    public void setSizeRangeInCharacters(IntegerRange sizeRangeInCharacters) {
        this.sizeRangeInCharacters = sizeRangeInCharacters;
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

}
