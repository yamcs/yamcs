package org.yamcs.xtce;

/**
 * 
 * Although XTCE suggests that this class could be used to encode/decode integer/float/string data, In Yamcs this is
 * used just for
 * encoding binary data (i.e. binary to binary). See {@link DataEncoding} for how to use the other classes to
 * encode/decode arbitrary binary to a
 * float/integer/string.
 * <p>
 * DIFFERS_FROM_XTCE: XTCE doesn't support LEADING_SIZE parameter types (it does only for strings). However it allows
 * the size to be specified dynamically
 * by the value of another parameter.
 *
 */
public class BinaryDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 3L;

    public enum Type {
        FIXED_SIZE, LEADING_SIZE, CUSTOM, DYNAMIC
    }

    int sizeInBitsOfSizeTag = 16; // this is used when type is LEADING_SIZE to encod the length of the value before the
                                  // value
    private Type type = Type.FIXED_SIZE;

    /**
     * For variable-sized parameters or arguments, a reference to the parameter
     * or argument containing the size.
     */
    protected DynamicIntegerValue dynamicSize;

    /**
     * copy constructor
     * 
     * @param bde
     */
    public BinaryDataEncoding(BinaryDataEncoding bde) {
        super(bde);
        this.sizeInBitsOfSizeTag = bde.sizeInBitsOfSizeTag;
        this.type = bde.type;
    }

    public BinaryDataEncoding(Builder builder) {
        super(builder, -1);

        if (builder.type != null) {
            this.type = builder.type;
        }
        if (builder.sizeInBitsOfSizeTag != null) {
            this.sizeInBitsOfSizeTag = builder.sizeInBitsOfSizeTag;
        }

        if (builder.dynamicSize != null) {
            this.sizeInBits = -1;
            this.dynamicSize = builder.dynamicSize;
        }

        if (builder.baseEncoding instanceof BinaryDataEncoding) {
            BinaryDataEncoding baseEncoding = (BinaryDataEncoding) builder.baseEncoding;

            if (builder.type == null) {
                this.type = baseEncoding.type;
            }

            if (builder.sizeInBitsOfSizeTag == null) {
                this.sizeInBitsOfSizeTag = baseEncoding.sizeInBitsOfSizeTag;
            }
            if (builder.dynamicSize == null) {
                this.dynamicSize = baseEncoding.dynamicSize;
            }
        }
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    public void setSizeType(Type sizeType) {
        this.type = sizeType;
    }

    public Type getType() {
        return type;
    }

    public int getSizeInBitsOfSizeTag() {
        return sizeInBitsOfSizeTag;
    }

    public void setSizeInBitsOfSizeTag(int sizeInBits) {
        this.sizeInBitsOfSizeTag = sizeInBits;
    }

    public boolean isVariableSize() {
        return dynamicSize != null;
    }

    public DynamicIntegerValue getDynamicSize() {
        return dynamicSize;
    }

    @Override
    public Object parseString(String stringValue) {
        return BinaryDataType.hexStringToArray(stringValue);
    }

    @Override
    public BinaryDataEncoding copy() {
        return new BinaryDataEncoding(this);
    }

    @Override
    public String toString() {
        return "BinaryDataEncoding(sizeInBits:" + sizeInBits + ", type:" + type + ")";
    }

    public static class Builder extends DataEncoding.Builder<Builder> {
        Integer sizeInBitsOfSizeTag;
        private Type type;
        DynamicIntegerValue dynamicSize;

        public Builder(BinaryDataEncoding encoding) {
            super(encoding);
            this.sizeInBitsOfSizeTag = encoding.sizeInBitsOfSizeTag;
            this.type = encoding.type;
        }

        public Builder() {
            super();
        }

        public DynamicIntegerValue getDynamicSize() {
            return dynamicSize;
        }

        public Builder setDynamicSize(DynamicIntegerValue v) {
            this.dynamicSize = v;
            return self();
        }

        public Builder setType(Type type) {
            this.type = type;
            return self();
        }

        public Builder setSizeInBitsOfSizeTag(int sizeInBitsOfSizeTag) {
            this.sizeInBitsOfSizeTag = sizeInBitsOfSizeTag;
            return self();
        }

        @Override
        public Builder setToBinaryTransformAlgorithm(Algorithm alg) {
            super.setToBinaryTransformAlgorithm(alg);
            this.type = Type.CUSTOM;
            return self();
        }

        @Override
        public Builder setFromBinaryTransformAlgorithm(Algorithm alg) {
            super.setFromBinaryTransformAlgorithm(alg);
            this.type = Type.CUSTOM;
            return self();
        }

        @Override
        public BinaryDataEncoding build() {
            return new BinaryDataEncoding(this);
        }
    }
}
