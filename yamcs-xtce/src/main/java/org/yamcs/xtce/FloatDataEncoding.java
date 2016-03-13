package org.yamcs.xtce;

import java.nio.ByteOrder;


public class FloatDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 200805131551L;

    public enum Encoding {IEEE754_1985, STRING}; //DIFFERS_FROM_XTCE
    Calibrator defaultCalibrator=null;
    private Encoding encoding;

    StringDataEncoding stringEncoding=null;

    /**
     * FloadDataEncoding of type {@link FloatDataEncoding.Encoding#IEEE754_1985}
     * 
     * @param name
     * @param sizeInBits
     */
    public FloatDataEncoding(String name, int sizeInBits) {
        this(name, sizeInBits, ByteOrder.BIG_ENDIAN);       
    }

    public FloatDataEncoding(String name, int sizeInBits, ByteOrder byteOrder) {
        super(name, sizeInBits, byteOrder);
        encoding = Encoding.IEEE754_1985;
    }
    /**
     * Float data encoded as a string. 
     * @param name
     * @param sde describes how the string is encoded
     */
    public FloatDataEncoding(String name, StringDataEncoding sde) {
        super(name, sde.getSizeInBits());
        encoding = Encoding.STRING;
        stringEncoding = sde;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public StringDataEncoding getStringDataEncoding() {
        return stringEncoding;
    }

    public Calibrator getDefaultCalibrator() {
        return defaultCalibrator;
    }

    public void setDefaultCalibrator(Calibrator calibrator) {
        this.defaultCalibrator = calibrator;
    }

    @Override
    public String toString() {
        switch(encoding) {
        case IEEE754_1985:
            return "FloatDataEncoding(sizeInBits="+sizeInBits+""
            +(defaultCalibrator==null?"":(", defaultCalibrator:"+defaultCalibrator)) 
            +")";
        case STRING:
            return "FloatDataEncoding(StringEncoding: "+stringEncoding
                    +(defaultCalibrator==null?"":(", defaultCalibrator:"+defaultCalibrator)) 
                    +")";
        default:
            return "UnknownFloatEncoding("+encoding+")";
        }

    }

    @Override
    public Object parseString(String stringValue) {
        switch(encoding) {
        case IEEE754_1985:
            if(sizeInBits==32) {
                return Float.parseFloat(stringValue);
            } else {
                return Double.parseDouble(stringValue);
            }
        case STRING:
            return stringValue;
        default:
            throw new IllegalStateException("Unknown encoding "+encoding);
        }
    }
}
