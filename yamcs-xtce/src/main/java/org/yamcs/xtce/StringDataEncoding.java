package org.yamcs.xtce;

import java.nio.charset.Charset;

/**
 * For common encodings of string data.
 * 
 * <p>
 * String data is encoded in a buffer. The size of the buffer may be fixed or variable. The size of the string may be
 * variable inside the buffer or may take the whole buffer.
 * <p>
 * Upon decoding a packet, the resulting raw value will be the string extracted from the buffer but the parameter is
 * considered to occupy the whole buffer (meaning that the next parameter will come in the packet after the end of the
 * buffer not after the end of the string)
 * 
 * <p>
 * The distinction between the string and the buffer containing the string has been made in Yamcs 5.5 in order to comply
 * better with the XTCE 1.2.
 * 
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
         * <p>
         * If this is used, the algorithm will also determine the size of the buffer.
         */
        CUSTOM
    };

    /**
     * If the buffer size is dynamic
     */
    private DynamicIntegerValue dynamicBufferSize;

    private SizeType sizeType;

    private byte terminationChar = 0; // it's in fact the terminationByte but we call it like this for compatibility
                                      // with XTCE
    int sizeInBytesOfSizeTag = 2;
    private String encoding = "UTF-8";

    private int maxSizeInBytes = -1;

    public StringDataEncoding(Builder builder) {
        super(builder, -1);

        this.sizeType = builder.sizeType;
        if (builder.terminationChar != null) {
            this.terminationChar = builder.terminationChar;
        }
        if (builder.sizeInBytesOfSizeTag != null) {
            this.sizeInBytesOfSizeTag = builder.sizeInBytesOfSizeTag;
        }
        if (builder.encoding != null) {
            this.encoding = builder.encoding;
        }
        this.maxSizeInBytes = builder.maxSizeInBytes;
        this.dynamicBufferSize = builder.dynamicBufferSize;

        if (builder.baseEncoding instanceof StringDataEncoding) {
            StringDataEncoding baseEncoding = (StringDataEncoding) builder.baseEncoding;

            if (builder.sizeType == null) {
                this.sizeType = baseEncoding.sizeType;
            }
            if (builder.terminationChar == null) {
                this.terminationChar = baseEncoding.terminationChar;
            }

            if (builder.sizeInBytesOfSizeTag == null) {
                this.sizeInBytesOfSizeTag = baseEncoding.sizeInBytesOfSizeTag;
            }

            if (builder.encoding == null) {
                this.encoding = baseEncoding.encoding;
            }

            if (builder.dynamicBufferSize == null) {
                this.dynamicBufferSize = baseEncoding.dynamicBufferSize;
            }
            if (builder.maxSizeInBytes == -1) {
                this.maxSizeInBytes = baseEncoding.maxSizeInBytes;
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
        this.sizeInBytesOfSizeTag = sde.sizeInBytesOfSizeTag;
        this.encoding = sde.encoding;
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    public void setSizeType(SizeType sizeType) {
        this.sizeType = sizeType;
    }

    public SizeType getSizeType() {
        return sizeType;
    }

    public int getSizeInBytesOfSizeTag() {
        return sizeInBytesOfSizeTag;
    }

    public int getSizeInBitsOfSizeTag() {
        return sizeInBytesOfSizeTag << 3;
    }

    public byte getTerminationChar() {
        return terminationChar;
    }

    public void setTerminationChar(byte tc) {
        this.terminationChar = tc;
    }

    public DynamicIntegerValue getDynamicBufferSize() {
        return dynamicBufferSize;
    }

    public int getMaxSizeInBytes() {
        return maxSizeInBytes;
    }

    public void setMaxSizeInBytes(int maxSizeInBytes) {
        this.maxSizeInBytes = maxSizeInBytes;
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
            } else if (dynamicBufferSize != null) {
                sb.append(", dynamicBufferSize=").append(dynamicBufferSize);
            }
            break;
        case TERMINATION_CHAR:
            sb.append("terminationChar=" + getTerminationChar());
            if (getSizeInBits() != -1) {
                sb.append(", sizeInBits=" + getSizeInBits());
            } else if (dynamicBufferSize != null) {
                sb.append(", dynamicBufferSize=").append(dynamicBufferSize);
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
        Integer sizeInBytesOfSizeTag = null;
        private String encoding = "UTF-8";
        private DynamicIntegerValue dynamicBufferSize;
        private int maxSizeInBytes = -1;

        public Builder(StringDataEncoding encoding) {
            super(encoding);
            this.sizeType = encoding.sizeType;
            this.terminationChar = encoding.terminationChar;
            this.sizeInBytesOfSizeTag = encoding.sizeInBytesOfSizeTag;
        }

        public Builder() {
            super();
        }

        @Override
        public StringDataEncoding build() {
            return new StringDataEncoding(this);
        }

        @Override
        public Builder setSizeInBits(Integer sizeInBits) {
            if (sizeInBits > 0) {
                if (sizeInBits % 8 != 0) {
                    throw new IllegalArgumentException("Size in bits for string encoding has to be multiple of 8.");
                }
                this.maxSizeInBytes = sizeInBits / 8;
            }
            super.setSizeInBits(sizeInBits);
            return self();
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
            if ((size & 7) != 0) {
                throw new IllegalArgumentException("Size in bits of size tag has to be a multiple of 8");
            }
            this.sizeInBytesOfSizeTag = size >> 3;
            return self();
        }

        public Builder setEncoding(String stringEncoding) {
            this.encoding = stringEncoding;
            return self();
        }

        public Builder setMaxSizeInBits(int maxSizeInBits) {
            if (maxSizeInBits % 8 != 0) {
                throw new IllegalArgumentException("Maximum size in bits for string encoding has to be multiple of 8.");
            }
            this.maxSizeInBytes = maxSizeInBits / 8;
            return self();
        }

        public Builder setDynamicBufferSize(DynamicIntegerValue div) {
            this.dynamicBufferSize = div;
            return self();
        }

        public SizeType getSizeType() {
            return sizeType;
        }
    }

}
