package org.yamcs.xtce;

import java.math.BigInteger;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * Contains an integral value.
 *
 * @author nm
 *
 */
public abstract class IntegerDataType extends NumericDataType {
    private static final long serialVersionUID = 1L;
    int sizeInBits = 32;
    protected boolean signed = true;
    Long initialValue;

    /**
     * XTCE: The Valid Range bounds the universe of possible values this Parameter may have. For Telemetry the valid
     * range is always applied before calibration, regardless of the value of validRangeAppliesToCalibrated. For
     * commanding, if validRangeAppliesToCalibrated is false -- it is applied before calibration to the link
     * DataEncoding.
     */
    IntegerValidRange validRange;
    /**
     * XTCE: This element provides the implementation with assistance rendering the value as a string for users.
     * <p>
     * Note that XTCE wraps NumberFormatType in another type ToStringType, which we don't do.
     */
    NumberFormatType numberFormat;

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
        if (builder.numberFormat != null) {
            numberFormat = builder.numberFormat;
        }

        if (builder.baseType instanceof IntegerDataType) {
            IntegerDataType baseType = (IntegerDataType) builder.baseType;
            if (builder.sizeInBits == null) {
                this.sizeInBits = baseType.sizeInBits;
            }
            if (builder.signed == null) {
                this.signed = baseType.signed;
            }

            if (builder.validRange == null && baseType.validRange != null) {
                this.validRange = baseType.validRange;
            }
            if (builder.numberFormat == null && baseType.numberFormat != null) {
                this.numberFormat = baseType.numberFormat;
            }
        }
        setInitialValue(builder);
    }

    protected IntegerDataType(IntegerDataType t) {
        super(t);
        this.sizeInBits = t.sizeInBits;
        this.signed = t.signed;
        this.validRange = t.validRange;
        this.numberFormat = t.numberFormat;
    }

    public boolean isSigned() {
        return signed;
    }

    public int getSizeInBits() {
        return sizeInBits;
    }

    @Override
    protected void setInitialValue(Object initialValue) {
        this.initialValue = convertType(initialValue);
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

    @Override
    public Long getInitialValue() {
        return (Long) initialValue;
    }

    public NumberFormatType getNumberFormat() {
        return numberFormat;
    }

    /**
     * In case the provided value is a String, it is parsed to a Long Base 10 (decimal) form unless:
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
    public Long convertType(Object value) {
        if (value instanceof Number) {
            if (value instanceof Double || value instanceof Float) {
                double d = ((Number) value).doubleValue();
                if (Double.isNaN(d) || d < MIN_LONG_AS_DOUBLE || d >= MAX_LONG_AS_DOUBLE_EXCLUSIVE) {
                    throw notFitException(value);
                }
            }
            long longValue = ((Number) value).longValue();
            checkFits(longValue);
            return longValue;
        } else if (value instanceof String) {
            String stringValue = (String) value;
            String sv = stringValue.replace("_", "");
            if (sv.length() == 0) {
                throw new NumberFormatException("Zero length string");
            }

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

            if (sv.startsWith("-", off) || sv.startsWith("+", off)) {
                throw new NumberFormatException("Sign character in the middle of the number");
            }
            BigInteger bn;
            try {
                bn = new BigInteger(sv.substring(off), radix);
            } catch (NumberFormatException e) {
                // Customize the message for better user experience
                throw new NumberFormatException("Not a valid integer");
            }

            int bs = sizeInBits;
            if (signed) {
                bs--;
            }
            if (bn.bitLength() > bs && !isSignedMinValue(bn, bs, negative)) {
                throw notFitException(stringValue);
            }
            long x = bn.longValue();
            if (negative) {
                x = -x;
            }
            return x;
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    /**
     * A negative value whose magnitude is exactly {@code 2^bs} is the smallest representable signed value (e.g. -128
     * for a signed 8 bit type), and its magnitude needs {@code bs + 1} bits to represent, one more than any
     * positive value of the same type. That is not an overflow.
     */
    private boolean isSignedMinValue(BigInteger bn, int bs, boolean negative) {
        return signed && negative && bn.bitLength() == bs + 1 && bn.equals(BigInteger.ONE.shiftLeft(bs));
    }

    // Long.MIN_VALUE (-2^63) is exactly representable as a double; Long.MAX_VALUE (2^63-1) is NOT
    // (it rounds up to 2^63), so the exclusive upper bound must be 2^63, not (double) Long.MAX_VALUE.
    private static final double MIN_LONG_AS_DOUBLE = -0x1p63; // -9223372036854775808.0 == Long.MIN_VALUE
    private static final double MAX_LONG_AS_DOUBLE_EXCLUSIVE = 0x1p63; // 9223372036854775808.0 == 2^63

    private void checkFits(long value) {
        if (sizeInBits >= 64) {
            // A signed 64 bit type covers the whole long range; an unsigned 64 bit type reinterprets
            // the same bit pattern as unsigned, so any long value is representable either way.
            return;
        }
        if (signed) {
            long max = (1L << (sizeInBits - 1)) - 1;
            long min = -max - 1;
            if (value < min || value > max) {
                throw notFitException(value);
            }
        } else {
            long max = (1L << sizeInBits) - 1;
            if (value < 0 || value > max) {
                throw notFitException(value);
            }
        }
    }

    private NumberFormatException notFitException(Object value) {
        return new NumberFormatException("Number " + value + " does not fit the bit size (" + sizeInBits
                + (signed ? "/signed" : "unsigned") + ")");
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

    public abstract static class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {

        Integer sizeInBits;
        Boolean signed;
        IntegerValidRange validRange;
        NumberFormatType numberFormat;

        public Builder() {
        }

        public Builder(IntegerDataType dataType) {
            super(dataType);
            this.sizeInBits = dataType.sizeInBits;
            this.signed = dataType.signed;
            this.validRange = dataType.validRange;
            this.numberFormat = dataType.numberFormat;
        }

        public T setSizeInBits(int sizeInBits) {
            this.sizeInBits = sizeInBits;
            return self();
        }

        public T setSigned(boolean signed) {
            this.signed = signed;
            return self();
        }

        public boolean isSigned() {
            return signed == null ? true : signed;
        }

        public T setValidRange(IntegerValidRange range) {
            this.validRange = range;
            return self();
        }

        public T setNumberFormat(NumberFormatType numberFormat) {
            this.numberFormat = numberFormat;
            return self();
        }
    }
}
