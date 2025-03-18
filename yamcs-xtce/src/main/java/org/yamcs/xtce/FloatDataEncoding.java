package org.yamcs.xtce;

/**
 * For common encodings of floating point data.
 * <p>
 * Unlike XTCE we support encoding floats as strings - this is done by providing a separate {@link StringDataEncoding}
 * 
 */
public class FloatDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 3L;

    public enum Encoding {
        IEEE754_1985, MILSTD_1750A, STRING// DIFFERS_FROM_XTCE
    };

    private Encoding encoding = Encoding.IEEE754_1985;

    StringDataEncoding stringEncoding = null;

    public FloatDataEncoding(Builder builder) {
        super(builder, 32);
        if (builder.encoding != null) {
            this.encoding = builder.encoding;
        }

        this.stringEncoding = builder.stringEncoding;

        if (builder.baseEncoding instanceof FloatDataEncoding) {
            FloatDataEncoding baseEncoding = (FloatDataEncoding) builder.baseEncoding;
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
        this.encoding = fde.encoding;
        this.stringEncoding = fde.stringEncoding;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public StringDataEncoding getStringDataEncoding() {
        return stringEncoding;
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


    @Override
    public FloatDataEncoding copy() {
        return new FloatDataEncoding(this);
    }

    public static class Builder extends DataEncoding.Builder<Builder> {
        Encoding encoding = null;
        StringDataEncoding stringEncoding = null;

        public Builder(FloatDataEncoding encoding) {
            super(encoding);
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
