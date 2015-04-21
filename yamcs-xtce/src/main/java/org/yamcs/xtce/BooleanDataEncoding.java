package org.yamcs.xtce;

/**
 * For boolean data.
 * DIFFERS_FROM_XTCE: XTCE does not have a BooleanDataEncoding, only a BooleanParameterType.
 * This creates an inconsistency when algorithms output uncalibrated boolean values.
 */
public class BooleanDataEncoding extends DataEncoding {
    private static final long serialVersionUID=200805131551L;
    public BooleanDataEncoding(String name) {
        super(name, 1);
    }

    @Override
    public String toString() {
        return "BooleanDataEncoding(sizeInBits:"+sizeInBits+")";
    }

    @Override
    public Object parseString(String stringValue) {
        return Boolean.parseBoolean(stringValue);
    }
}
