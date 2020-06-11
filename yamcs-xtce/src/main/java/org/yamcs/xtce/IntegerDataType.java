package org.yamcs.xtce;

import java.math.BigInteger;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * Contains an integral value.
 * 
 *
 * @author nm
 *
 */
public abstract class IntegerDataType extends NumericDataType {
    private static final long serialVersionUID = 1L;
    int sizeInBits = 32;
    protected boolean signed = true;

    /**
     * XTCE: The Valid Range bounds the universe of possible values this Parameter may have.
     * For Telemetry the valid range is always applied before calibration, regardless of the value of
     * validRangeAppliesToCalibrated.
     * For commanding, if validRangeAppliesToCalibrated is false -- it is applied before calibration to the link
     * DataEncoding.
     */
    IntegerValidRange validRange;

    protected IntegerDataType(Builder<?> builder) {
        super(builder);

        if (builder.sizeInBits != null) {
            this.sizeInBits = builder.sizeInBits;
        }
        if (builder.signed != null) {
            signed = builder.signed;
        }
        if (builder.validRange != null) {
            validRange = builder.validRange;
        }
        if (builder.initialValue != null) {
            this.initialValue = parseString(builder.initialValue);
        }
    }

    protected IntegerDataType(IntegerDataType t) {
        super(t);
        this.sizeInBits = t.sizeInBits;
        this.signed = t.signed;
        this.validRange = t.validRange;
    }

    public boolean isSigned() {
        return signed;
    }

    public int getSizeInBits() {
        return sizeInBits;
    }

    /**
     * returns the range for the values of this type to be valid or null if there is no range set (meaning that all
     * values are valid)
     * 
     * @return
     */
    public IntegerValidRange getValidRange() {
        return validRange;
    }

    public void setInitialValue(Long initialValue) {
        this.initialValue = initialValue;
    }

    public Long getInitialValue() {
        return (Long) initialValue;
    }

    /**
     * Parses the string into a Long
     * Base 10 (decimal) form is assumed unless:
     * <ul>
     * <li>if preceded by a 0b or 0B, value is in base two (binary form)</li>
     * <li>if preceded by a 0o or 0O, values is in base 8 (octal) form</li>
     * <li>if preceded by a 0x or 0X, value is in base 16 (hex) form.</li>
     * </ul>
     * 
     * Underscores (_) are allowed in the string and ignored.
     * 
     * Throws a {@link NumberFormatException} if the value cannot be parsed or does not fit within the specified number
     * of bits
     */
    @Override
    public Long parseString(String stringValue) {
        String sv = stringValue.replace("_", "");
        if (sv.length() == 0)
            throw new NumberFormatException("Zero length string");

        int off = 0;

        char sv0 = sv.charAt(0);
        boolean negative = false;
        int radix = 10;

        if (sv0 == '-') {
            if (!signed) {
                throw new NumberFormatException("negative number specified for unsigned integer");
            }
            negative = true;
            off++;
        } else if (sv0 == '+') {
            off++;
        }

        if (sv.startsWith("0b", off) || sv.startsWith("0B", off)) {
            off += 2;
            radix = 2;
        } else if (sv.startsWith("0o", off) || sv.startsWith("0O", off)) {
            off += 2;
            radix = 8;
        } else if (sv.startsWith("0x", off) || sv.startsWith("0X", off)) {
            off += 2;
            radix = 16;
        }

        if (sv.startsWith("-", off) || sv.startsWith("+", off))
            throw new NumberFormatException("Sign character in the middle of the number");
        BigInteger bn = new BigInteger(sv.substring(off), radix);

        int bs = sizeInBits;
        if (signed) {
            bs--;
        }
        if (bn.bitLength() > bs) {
            throw new NumberFormatException("Number " + stringValue + " does not fit the bit size (" + sizeInBits
                    + (signed ? "/signed" : "unsigned") + ")");
        }
        long x = bn.longValue();
        if (negative) {
            x = -x;
        }
        return x;
    }

    @Override
    public Type getValueType() {
        return sizeInBits > 32 ? (signed ? Type.SINT64 : Type.UINT64)
                : (signed ? Type.SINT32 : Type.UINT32);
    }

    @Override
    public String getTypeAsString() {
        return "integer";
    }

    public abstract static class Builder<T extends Builder<T>>  extends BaseDataType.Builder<T> {

        Integer sizeInBits;
        Boolean signed;
        IntegerValidRange validRange;

        public Builder() {
        }
        
        public Builder(IntegerDataType dataType) {
            super(dataType);
            this.sizeInBits = dataType.sizeInBits;
            this.signed = dataType.signed;
            this.validRange = dataType.validRange;
        }
        
        public void setSizeInBits(int sizeInBits) {
            this.sizeInBits = sizeInBits;
        }

        public void setSigned(boolean signed) {
            this.signed = signed;
        }

        public boolean isSigned() {
            return signed == null ? true : signed;
        }

        public void setValidRange(IntegerValidRange range) {
            this.validRange = range;
        }
    }
}
