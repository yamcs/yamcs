package org.yamcs.xtce;

public class StringDataType extends BaseDataType {
    private static final long serialVersionUID = 2L;

    /**
     * For telemetry, specify as UTF-8 or UTF-16 value to match the encoding. 
     * This range check will be applied before conversion to the host string data type.  
     * For commanding, the range check occurs on the string host data type encoding, 
     * whatever that is -- before injection on the command link.
     */
    IntegerRange sizeRangeInCharacters; 

    /**
     * Initial value - used as default value for arguments
     */
    String initialValue;
    StringDataType(String name) {
        super(name);
    }


    public String getInitialValue() {
        return initialValue;
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
}
