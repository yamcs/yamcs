package org.yamcs.xtce;

import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For all major encodings of integer data
 *
 */
public class IntegerDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 4L;
    static Logger log = LoggerFactory.getLogger(IntegerDataEncoding.class.getName());

    public enum Encoding {
        UNSIGNED, TWOS_COMPLEMENT, SIGN_MAGNITUDE, ONES_COMPLEMENT, STRING
    };

    Encoding encoding = Encoding.UNSIGNED;
    StringDataEncoding stringEncoding = null;

    public IntegerDataEncoding(Builder builder) {
        super(builder, 8);

        if (builder.encoding != null) {
            this.encoding = builder.encoding;
        }

        this.stringEncoding = builder.stringEncoding;

        if (builder.baseEncoding instanceof IntegerDataEncoding) {
            IntegerDataEncoding baseEncoding = (IntegerDataEncoding) builder.baseEncoding;

            if (builder.encoding == null) {
                this.encoding = baseEncoding.encoding;
            }

            if (builder.stringEncoding == null) {
                this.stringEncoding = baseEncoding.stringEncoding;
            }
        }
    }

    public IntegerDataEncoding(IntegerDataEncoding ide) {
        super(ide);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public StringDataEncoding getStringEncoding() {
        return stringEncoding;
    }

    public void setEncoding(Encoding encoding) {
        this.encoding = encoding;
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
        if (defaultCalibrator != null) {
            sb.append(", defaultCalibrator: ").append(defaultCalibrator);
        }
        if (contextCalibratorList != null) {
            sb.append(", contextCalibrators: ").append(contextCalibratorList);
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public IntegerDataEncoding copy() {
        return new IntegerDataEncoding(this);
    }

    public static class Builder extends DataEncoding.Builder<Builder> {
        Encoding encoding = null;
        StringDataEncoding stringEncoding = null;

        public Builder(IntegerDataEncoding encoding) {
            super(encoding);
            this.encoding = encoding.encoding;
            this.stringEncoding = encoding.stringEncoding;
        }

        public Builder() {
            super();
        }

        public IntegerDataEncoding build() {
            return new IntegerDataEncoding(this);
        }

        public Builder setStringEncoding(StringDataEncoding stringEncoding) {
            this.stringEncoding = stringEncoding;
            this.sizeInBits = stringEncoding.sizeInBits;
            this.encoding = Encoding.STRING;
            return self();
        }

        public Builder setEncoding(Encoding enc) {
            this.encoding = enc;
            return self();
        }

        public Encoding getEncoding() {
            return encoding;
        }

        public Builder setByteOrder(ByteOrder byteOrder) {
            this.byteOrder = byteOrder;
            return self();
        }
    }
}
