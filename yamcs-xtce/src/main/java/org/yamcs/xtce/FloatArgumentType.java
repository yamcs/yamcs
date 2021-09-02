package org.yamcs.xtce;

/**
 * Represent aspects of an float, probably using IntegerDataEncoding with a calibrator or FloatDataEncoding.
 * 
 * @author nm
 *
 */
public class FloatArgumentType extends FloatDataType implements ArgumentType {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a shallow copy.
     */
    public FloatArgumentType(FloatArgumentType t) {
        super(t);
    }

    public FloatArgumentType(Builder builder) {
        super(builder);
    }

    @Override
    public String getTypeAsString() {
        return "float";
    }

    @Override
    public FloatArgumentType.Builder toBuilder() {
        return new FloatArgumentType.Builder(this);
    }

    @Override
    public String toString() {
        return "FloatArgumentType name:" + name + " sizeInBits:" + sizeInBits + " encoding:" + encoding;
    }

    public static class Builder extends FloatDataType.Builder<Builder> implements ArgumentType.Builder<Builder> {

        public Builder(FloatArgumentType floatArgumentType) {
            super(floatArgumentType);
        }

        public Builder() {
        }

        @Override
        public FloatArgumentType build() {
            return new FloatArgumentType(this);
        }
    }
}
