package org.yamcs.xtce;

import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * For common encodings of floating point data.
 * 
 * Unlike XTCE we support encoding floats as strings  - this is done by providing a separate {@link StringDataEncoding} 
 * @author nm
 *
 */
public class FloatDataEncoding extends DataEncoding implements NumericDataEncoding {
    private static final long serialVersionUID = 3L;

    public enum Encoding {
        IEEE754_1985, 
        MILSTD_1750A,
        STRING// DIFFERS_FROM_XTCE 
    }; 

    Calibrator defaultCalibrator = null;
    private List<ContextCalibrator> contextCalibratorList = null;
    
    private final Encoding encoding;

    StringDataEncoding stringEncoding = null;

    /**
     * FloadDataEncoding of type {@link FloatDataEncoding.Encoding#IEEE754_1985}
     * 
     * @param sizeInBits
     */
    public FloatDataEncoding(int sizeInBits) {
        this(sizeInBits, ByteOrder.BIG_ENDIAN, Encoding.IEEE754_1985);
    }

    public FloatDataEncoding(int sizeInBits, ByteOrder byteOrder, Encoding encoding) {
        super(sizeInBits, byteOrder);
        validateEncodingSizeInBits(encoding, sizeInBits);
        this.encoding = encoding;
    }

    private static void validateEncodingSizeInBits(Encoding encoding, int sizeInBits) {
        if(encoding==Encoding.IEEE754_1985 ) {
            if(sizeInBits!=32 && sizeInBits!=64) {
                throw new IllegalArgumentException("For IEEE754_1985 encoding sizeInBits has to be 32 or 64");
            }
        } else if(encoding==Encoding.MILSTD_1750A) {
            if(sizeInBits!=32 && sizeInBits!=48) {
                throw new IllegalArgumentException("For MILSTD_1750A encoding sizeInBits has to be 32 or 48");
            }
        }
    }

    /**
     * Float data encoded as a string.
     * 
     * @param sde
     *            describes how the string is encoded
     */
    public FloatDataEncoding(StringDataEncoding sde) {
        super(sde.getSizeInBits());
        this.encoding = Encoding.STRING;
        stringEncoding = sde;
    }

    public FloatDataEncoding(int sizeInBits, Encoding encoding) {
        this(sizeInBits, ByteOrder.BIG_ENDIAN, encoding);
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
        switch (getEncoding()) {
        case IEEE754_1985:
        case MILSTD_1750A:
            return "FloatDataEncoding(sizeInBits=" + sizeInBits + ""
                    + (defaultCalibrator == null ? "" : (", defaultCalibrator:" + defaultCalibrator))
                    + ")";
        case STRING:
            return "FloatDataEncoding(StringEncoding: " + stringEncoding
                    + (defaultCalibrator == null ? "" : (", defaultCalibrator:" + defaultCalibrator))
                    + ")";
        default:
            return "UnknownFloatEncoding(" + getEncoding() + ")";
        }

    }

    @Override
    public Object parseString(String stringValue) {
        switch (getEncoding()) {
        case IEEE754_1985:
        case MILSTD_1750A:
            if (sizeInBits == 32) {
                return Float.parseFloat(stringValue);
            } else {
                return Double.parseDouble(stringValue);
            }
        case STRING:
            return stringValue;
        default:
            throw new IllegalStateException("Unknown encoding " + getEncoding());
        }
    }


    public List<ContextCalibrator> getContextCalibratorList() {
        return contextCalibratorList;
    }

    public void setContextCalibratorList(List<ContextCalibrator> contextCalibratorList) {
        this.contextCalibratorList = contextCalibratorList;
    }
    
    @Override
    public Set<Parameter> getDependentParameters() {
        if(contextCalibratorList!=null) {
            Set<Parameter> r = new HashSet<>();
            for(ContextCalibrator cc: contextCalibratorList) {
                r.addAll(cc.getContextMatch().getDependentParameters());
            }
            return r;
        } else {
            return Collections.emptySet();
        }
    }
}
