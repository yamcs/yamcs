package org.yamcs.xtce;

import java.nio.charset.Charset;

/**
 * For common encodings of string data
 * 
 * @author nm
 */
public class StringDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 1L;

    public enum SizeType {
        /**
         * fixed size has to be specified in the {@link #getSizeInBits}
         */
        FIXED,
        /**
         * Like C strings, they are terminated with a special string, usually a null character.
         */
        TERMINATION_CHAR,
        /**
         * Like PASCAL strings, the size of the string is given as an integer at the start of the string. SizeTag must
         * be an unsigned Integer
         */
        LEADING_SIZE,
        /**
         * {@link #getFromBinaryTransformAlgorithm} will be used to decode the data
         */
        CUSTOM
    };

    private SizeType sizeType;
    private byte terminationChar = 0; // it's in fact the terminationByte but we call it like this for compatibility
                                      // with XTCE
    int sizeInBitsOfSizeTag = 16;
    private String encoding = "UTF-8";

    public StringDataEncoding(Builder builder) {
        super(builder, -1);
        
        this.sizeType = builder.sizeType;
        if (builder.terminationChar != null) {
            this.terminationChar = builder.terminationChar;
        }
        if (builder.sizeInBitsOfSizeTag != null) {
            this.sizeInBitsOfSizeTag = builder.sizeInBitsOfSizeTag;
        }
        if (builder.encoding != null) {
            this.encoding = builder.encoding;
        }

        if (builder.baseEncoding != null && builder.baseEncoding instanceof StringDataEncoding) {
            StringDataEncoding baseEncoding = (StringDataEncoding) builder.baseEncoding;

            if (builder.sizeType == null) {
                this.sizeType = baseEncoding.sizeType;
            }
            if (builder.terminationChar == null) {
                this.terminationChar = baseEncoding.terminationChar;
            }

            if (builder.sizeInBitsOfSizeTag == null) {
                this.sizeInBitsOfSizeTag = baseEncoding.sizeInBitsOfSizeTag;
            }

            if (builder.encoding == null) {
                this.encoding = baseEncoding.encoding;
            }
        }
    }

    /**
     * copy constructor
     * 
     * @param sde
     */
    StringDataEncoding(StringDataEncoding sde) {
        super(sde);
        this.sizeType = sde.sizeType;
        this.terminationChar = sde.terminationChar;
        this.sizeInBitsOfSizeTag = sde.sizeInBitsOfSizeTag;
        this.encoding = sde.encoding;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public void setSizeType(SizeType sizeType) {
        this.sizeType = sizeType;
    }

    public SizeType getSizeType() {
        return sizeType;
    }

    public int getSizeInBitsOfSizeTag() {
        return sizeInBitsOfSizeTag;
    }

    public void setSizeInBitsOfSizeTag(int sizeInBits) {
        this.sizeInBitsOfSizeTag = sizeInBits;
    }

    public byte getTerminationChar() {
        return terminationChar;
    }

    public void setTerminationChar(byte tc) {
        this.terminationChar = tc;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("StringDataEncoding size: ");
        sb.append(getSizeType()).append("(");
        switch (getSizeType()) {
        case FIXED:
            sb.append("fixedSizeInBits=" + getSizeInBits());
            break;
        case LEADING_SIZE:
            sb.append("sizeInBitsOfSizeTag=" + getSizeInBitsOfSizeTag());
            if (getSizeInBits() != -1) {
                sb.append(", sizeInBits=" + getSizeInBits());
            }
            break;
        case TERMINATION_CHAR:
            sb.append("terminationChar=" + getTerminationChar());
            if (getSizeInBits() != -1) {
                sb.append(", sizeInBits=" + getSizeInBits());
            }
            break;
        case CUSTOM:
            sb.append(getFromBinaryTransformAlgorithm());
            break;
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public Object parseString(String stringValue) {
        return stringValue;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        // this will throw an exception if the charset is not supported by java
        Charset.forName(encoding);

        this.encoding = encoding;
    }

    @Override
    public StringDataEncoding copy() {
        return new StringDataEncoding(this);
    }

    public static class Builder extends DataEncoding.Builder<Builder> {
        private SizeType sizeType;
        private Byte terminationChar = null;
        Integer sizeInBitsOfSizeTag = null;
        private String encoding = "UTF-8";

        public Builder(StringDataEncoding encoding) {
            super(encoding);
            this.sizeType = encoding.sizeType;
            this.terminationChar = encoding.terminationChar;
            this.sizeInBitsOfSizeTag = encoding.sizeInBitsOfSizeTag;
        }

        public Builder() {
            super();
        }

        public StringDataEncoding build() {
            return new StringDataEncoding(this);
        }

        public Builder setSizeType(SizeType sizeType) {
            this.sizeType = sizeType;
            return self();
        }

        public Builder setTerminationChar(byte terminationChar) {
            this.terminationChar = terminationChar;
            return self();
        }

        public Builder setSizeInBitsOfSizeTag(int size) {
            this.sizeInBitsOfSizeTag = size;
            return self();
        }

        public Builder setEncoding(String stringEncoding) {
            this.encoding = stringEncoding;
            return self();
        }

        public SizeType getSizeType() {
            return sizeType;
        }
    }
}
