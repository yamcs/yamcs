package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;

public class BinaryDataType extends BaseDataType {
    private static final long serialVersionUID = 1L;

    /**
     * DIFFERS_FROM_XTCE XTCE does not define a size range
     */
    IntegerRange sizeRangeInBytes; 

    byte[] initialValue;


    protected BinaryDataType(String name) {
        super(name);
    }
    
    protected BinaryDataType(BinaryDataType t) {
        super(t);
        this.initialValue = t.initialValue;
        this.sizeRangeInBytes = t.sizeRangeInBytes;
    }

    public byte[] getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(byte[] initialValue) {
        this.initialValue = initialValue;
    }


    public IntegerRange getSizeRangeInBytes() {
        return sizeRangeInBytes;
    }


    public void setSizeRangeInBytes(IntegerRange sizeRangeInBytes) {
        this.sizeRangeInBytes = sizeRangeInBytes;
    }

    @Override
    public String toString(){ 
        return "BinaryData encoding: "+encoding;
    }
    
    /**
     * parse the hexadecimal stringValue into byte[]
     */
    @Override
    public Object parseString(String stringValue) {
        return hexStringToArray(stringValue);
    }


    /**
     * Converts a hex string into a byte array. 
     * If the string has an odd number of hex digits, a 0 is prepended in front.
     * 
     * if the string contains something else than 0-9, a-f, a NumberFormatException is thrown from Integer.parseInt with radix 16
     * 
     * @param s
     * @return byte array resulted from parsing the string
     */
    public static byte[] hexStringToArray(String s) {
        if((s.length() & 1) == 1) {
            s="0"+s;
        }
        byte[] b=new byte[s.length()/2];
        for(int i=0;i<s.length()/2;i++) {
            b[i]=(byte)(Integer.parseInt(s.substring(2*i,2*i+2),16)&0xFF);
        }
        return b;
    }

    /**
     * sets the initial value which is interpreted as a hex string
     */
    @Override
    public void setInitialValue(String hexString) {
        setInitialValue(StringConverter.hexStringToArray(hexString));
    }

    @Override
    public Type getValueType() {
        return Type.BINARY;
    }

    @Override
    public String getTypeAsString() {
        return "binary";
    }
}
