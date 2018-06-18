package org.yamcs.xtce;

import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * For all major encodings of integer data
 * 
 * @author nm
 *
 */
public class IntegerDataEncoding extends DataEncoding implements NumericDataEncoding {
    private static final long serialVersionUID = 3L;
    static Logger log = LoggerFactory.getLogger(IntegerDataEncoding.class.getName());
    
    Calibrator defaultCalibrator = null;
    private List<ContextCalibrator> contextCalibratorList = null;
    

    public enum Encoding {
        UNSIGNED, TWOS_COMPLEMENT, SIGN_MAGNITUDE, ONES_COMPLEMENT, STRING
    };

    Encoding encoding = Encoding.UNSIGNED;
    StringDataEncoding stringEncoding = null;

    /**
     * IntegerDataEncoding of type {@link IntegerDataEncoding.Encoding#UNSIGNED}
     * 
     * @param sizeInBits
     * @param byteOrder
     */
    public IntegerDataEncoding(int sizeInBits, ByteOrder byteOrder) {
        super(sizeInBits, byteOrder);
    }

    public IntegerDataEncoding(int sizeInBits) {
        super(sizeInBits, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Integer data encoded as a string.
     * 
     * @param name
     * @param sde
     *            describes how the string is encoded.
     */
    public IntegerDataEncoding(String name, StringDataEncoding sde) {
        super(sde.getSizeInBits());
        encoding = Encoding.STRING;
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
    public Object parseString(String stringValue) {
        if (encoding == Encoding.STRING) {
            return stringValue;
        }

        if (sizeInBits > 32) {
            return Long.decode(stringValue);
        } else {
            return Long.decode(stringValue).intValue();
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
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IntegerDataEncoding[sizeInBits: ").append(sizeInBits)
        .append(", byteOrder: ").append(byteOrder);
        if (stringEncoding == null) {
            sb.append(", encoding:").append(encoding);
        } else {
            sb.append(", stringEncoding: ").append(stringEncoding);
        }
        if(defaultCalibrator!=null) {
            sb.append(", defaultCalibrator: ").append(defaultCalibrator);
        }
        if(contextCalibratorList!=null) {
            sb.append(", contextCalibrators: ").append(contextCalibratorList);
        }
        sb.append("]");
        return sb.toString();
    }

}
