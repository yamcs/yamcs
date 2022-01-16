package org.yamcs.xtce;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * For common encodings of floating point data.
 * <p>
 * Unlike XTCE we support encoding floats as strings - this is done by providing a separate {@link StringDataEncoding}
 * 
 * @author nm
 *
 */
public class FloatDataEncoding extends DataEncoding implements NumericDataEncoding {
    private static final long serialVersionUID = 3L;

    public enum Encoding {
        IEEE754_1985, MILSTD_1750A, STRING// DIFFERS_FROM_XTCE
    };

    Calibrator defaultCalibrator = null;
    private List<ContextCalibrator> contextCalibratorList = null;

    private Encoding encoding = Encoding.IEEE754_1985;

    StringDataEncoding stringEncoding = null;

    public FloatDataEncoding(Builder builder) {
        super(builder, 32);
        if (builder.encoding != null) {
            this.encoding = builder.encoding;
        }

        this.defaultCalibrator = builder.defaultCalibrator;
        this.contextCalibratorList = builder.contextCalibratorList;

        this.stringEncoding = builder.stringEncoding;

        if (builder.baseEncoding instanceof FloatDataEncoding) {
            FloatDataEncoding baseEncoding = (FloatDataEncoding) builder.baseEncoding;
            if (builder.defaultCalibrator == null) {
                this.defaultCalibrator = baseEncoding.defaultCalibrator;
            }

            if (builder.contextCalibratorList == null) {
                this.contextCalibratorList = baseEncoding.contextCalibratorList;
            }

            if (builder.encoding == null) {
                this.encoding = baseEncoding.encoding;
            }

            if (builder.stringEncoding == null) {
                this.stringEncoding = baseEncoding.stringEncoding;
            }
        }

        validateEncodingSizeInBits(encoding, sizeInBits);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    private static void validateEncodingSizeInBits(Encoding encoding, int sizeInBits) {
        if (encoding == Encoding.IEEE754_1985) {
            if (sizeInBits != 32 && sizeInBits != 64) {
                throw new IllegalArgumentException("For IEEE754_1985 encoding sizeInBits has to be 32 or 64");
            }
        } else if (encoding == Encoding.MILSTD_1750A) {
            if (sizeInBits != 32 && sizeInBits != 48) {
                throw new IllegalArgumentException("For MILSTD_1750A encoding sizeInBits has to be 32 or 48");
            }
        }
    }

    /**
     * copy constructor
     * 
     * @param fde
     */
    public FloatDataEncoding(FloatDataEncoding fde) {
        super(fde);
        this.defaultCalibrator = fde.defaultCalibrator;
        this.contextCalibratorList = fde.contextCalibratorList;
        this.encoding = fde.encoding;
        this.stringEncoding = fde.stringEncoding;
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
            return "FloatDataEncoding(sizeInBits=" + sizeInBits + ", byteOrder: " + byteOrder
                    + (defaultCalibrator == null ? "" : (", defaultCalibrator:" + defaultCalibrator))
                    + ")";
        case STRING:
            return "FloatDataEncoding(sizeInBits=" + sizeInBits + " StringEncoding: " + stringEncoding
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
        if (contextCalibratorList != null) {
            Set<Parameter> r = new HashSet<>();
            for (ContextCalibrator cc : contextCalibratorList) {
                r.addAll(cc.getContextMatch().getDependentParameters());
            }
            return r;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public FloatDataEncoding copy() {
        return new FloatDataEncoding(this);
    }

    public static class Builder extends DataEncoding.Builder<Builder> implements NumericDataEncoding.Builder<Builder> {
        Calibrator defaultCalibrator = null;
        List<ContextCalibrator> contextCalibratorList = null;
        Encoding encoding = null;
        StringDataEncoding stringEncoding = null;

        public Builder(FloatDataEncoding encoding) {
            super(encoding);
            this.defaultCalibrator = encoding.defaultCalibrator;
            this.contextCalibratorList = encoding.contextCalibratorList;
            this.stringEncoding = encoding.stringEncoding;
            this.encoding = encoding.encoding;
        }

        public Builder() {
            super();
        }

        public FloatDataEncoding build() {
            return new FloatDataEncoding(this);
        }

        public Builder setFloatEncoding(Encoding floatEncoding) {
            this.encoding = floatEncoding;
            return self();
        }

        public Builder setDefaultCalibrator(Calibrator calibrator) {
            this.defaultCalibrator = calibrator;
            return self();
        }

        public Builder setContextCalibratorList(List<ContextCalibrator> list) {
            this.contextCalibratorList = list;
            return self();
        }

        public Builder setStringEncoding(StringDataEncoding stringEncoding) {
            this.stringEncoding = stringEncoding;
            this.encoding = Encoding.STRING;
            this.sizeInBits = stringEncoding.sizeInBits;
            return self();
        }

        public Encoding getFloatEncoding() {
            return encoding;
        }
    }
}
