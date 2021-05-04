package org.yamcs.xtce;

/**
 * For boolean data.
 * <p>
 * DIFFERS_FROM_XTCE: XTCE does not have a BooleanDataEncoding, only a BooleanParameterType. This
 * creates an inconsistency when algorithms output uncalibrated boolean values.
 */
public class BooleanDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 200805131551L;

    BooleanDataEncoding(BooleanDataEncoding bde) {
        super(bde);
    }

    public BooleanDataEncoding(Builder builder) {
        super(builder, 1);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public Object parseString(String stringValue) {
        return Boolean.parseBoolean(stringValue);
    }

    @Override
    public BooleanDataEncoding copy() {
        return new BooleanDataEncoding(this);
    }

    public static class Builder extends DataEncoding.Builder<Builder> {
        public Builder(BooleanDataEncoding encoding) {
            super(encoding);
        }

        public Builder() {
            super();
        }

        public BooleanDataEncoding build() {
            return new BooleanDataEncoding(this);
        }
    }

    @Override
    public String toString() {
        return "BooleanDataEncoding(sizeInBits:" + sizeInBits + ")";
    }
}
