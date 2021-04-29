package org.yamcs.xtce;

public class BinaryArgumentType extends BinaryDataType implements ArgumentType {
    private static final long serialVersionUID = 1L;

    BinaryArgumentType(Builder builder) {
        super(builder);
    }

    public BinaryArgumentType(BinaryArgumentType t1) {
        super(t1);
    }

    @Override
    public String getTypeAsString() {
        return "binary";
    }

    @Override
    public String toString() {
        return "BinaryArgumentType name:" + name + " encoding:" + encoding;
    }

    public BinaryArgumentType.Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder extends BinaryDataType.Builder<Builder> implements ArgumentType.Builder<Builder> {
        public Builder() {
        }

        public Builder(BinaryArgumentType binaryArgumentType) {
            super(binaryArgumentType);
        }

        @Override
        public BinaryArgumentType build() {
            return new BinaryArgumentType(this);
        }
    }

}
