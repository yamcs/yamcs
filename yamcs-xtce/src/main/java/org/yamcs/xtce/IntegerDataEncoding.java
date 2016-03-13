package org.yamcs.xtce;

import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntegerDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 200805131551L;
    static Logger log=LoggerFactory.getLogger(IntegerDataEncoding.class.getName());
    Calibrator defaultCalibrator=null;
    public enum Encoding {unsigned, twosCompliment, onesCompliment, signMagnitude, BCD, packedBCD, string};
    Encoding encoding=Encoding.unsigned;
    StringDataEncoding stringEncoding=null;

    /**
     * IntegerDataEncoding of type {@link IntegerDataEncoding.Encoding#unsigned}
     * @param name
     * @param sizeInBits
     */
    public IntegerDataEncoding(String name,int sizeInBits, ByteOrder byteOrder) {
        super(name, sizeInBits, byteOrder);
    }

    public IntegerDataEncoding(String name,int sizeInBits) {
        super(name, sizeInBits, ByteOrder.BIG_ENDIAN);
    }
    /**
     * Integer data encoded as a string.
     * @param name
     * @param sde describes how the string is encoded.
     */
    public IntegerDataEncoding(String name, StringDataEncoding sde) {
        super(name, sde.getSizeInBits());
        encoding = Encoding.string;
        stringEncoding = sde;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public StringDataEncoding getStringEncoding() {
        return stringEncoding;
    }

    public Calibrator getDefaultCalibrator() {
        return defaultCalibrator;
    }


    public void setEncoding(Encoding encoding) {
        this.encoding = encoding;
    }

    public void setDefaultCalibrator(Calibrator calibrator) {
        this.defaultCalibrator = calibrator;
    }

    @Override
    public String toString() {
        if( stringEncoding == null ) {
            return "IntegerDataEncoding(sizeInBits:"+sizeInBits+", encoding:"+encoding+", defaultCalibrator:"+defaultCalibrator+" byteOrder:"+byteOrder+")";
        } else {
            return "IntegerDataEncoding(StringEncoding: "+stringEncoding+" defaultCalibrator:"+defaultCalibrator+" byteOrder:"+byteOrder+")";
        }
    }

    @Override
    public Object parseString(String stringValue) {
        if(encoding == Encoding.string) return stringValue;
        
        if(sizeInBits>32) {
            return Long.decode(stringValue);
        } else {
            return Long.decode(stringValue).intValue();
        }
    }

   
}
