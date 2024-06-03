package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public abstract class FloatDataType extends NumericDataType {
    private static final long serialVersionUID = 1L;
    Double initialValue;
    /**
     * XTCE: The Valid Range bounds the universe of possible values this Parameter may have. For Telemetry the valid
     * range is always applied before calibration, regardless of the value of validRangeAppliesToCalibrated. For
     * commanding, if validRangeAppliesToCalibrated is false -- it is applied before calibration to the link
     * DataEncoding.
     * 
     */
    FloatValidRange validRange;
    int sizeInBits = 32;
    /**
     * XTCE: This element provides the implementation with assistance rendering the value as a string for users.
     * <p>
     * Note that XTCE wraps NumberFormatType in another type ToStringType, which we don't do.
     */
    NumberFormatType numberFormat;

    protected FloatDataType(Builder<?> builder) {
        super(builder);

        this.validRange = builder.validRange;
        this.numberFormat = builder.numberFormat;
        if (builder.sizeInBits != null) {
            this.sizeInBits = builder.sizeInBits;
        }

        if (builder.baseType instanceof FloatDataType) {
            FloatDataType baseType = (FloatDataType) builder.baseType;
            if (builder.sizeInBits == null) {
                this.sizeInBits = baseType.sizeInBits;
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

    protected FloatDataType(FloatDataType t) {
        super(t);
        this.validRange = t.validRange;
        this.sizeInBits = t.sizeInBits;
        this.numberFormat = t.numberFormat;
    }

    @Override
    public Double getInitialValue() {
        return initialValue;
    }

    @Override
    protected void setInitialValue(Object initialValue) {
        this.initialValue = convertType(initialValue);
    }

    public int getSizeInBits() {
        return sizeInBits;
    }

    public FloatValidRange getValidRange() {
        return validRange;
    }

    public NumberFormatType getNumberFormat() {
        return numberFormat;
    }

    @Override
    public Double convertType(Object value) {
        if (value instanceof String) {
            String stringValue = (String) value;
            try {
                if (sizeInBits == 32) {
                    return (double) Float.parseFloat(stringValue);
                } else {
                    return Double.parseDouble(stringValue);
                }
            } catch (NumberFormatException e) {
                // Customize the message for better user experience
                throw new NumberFormatException("Not a valid float");
            }
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    @Override
    public Type getValueType() {
        return (sizeInBits <= 32) ? Type.FLOAT : Type.DOUBLE;
    }

    @Override
    public String getTypeAsString() {
        return "float";
    }

    public abstract static class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {
        private FloatValidRange validRange;
        Integer sizeInBits;
        private NumberFormatType numberFormat;

        public Builder() {
        }

        public Builder(FloatDataType dataType) {
            super(dataType);
            this.validRange = dataType.validRange;
            this.sizeInBits = dataType.sizeInBits;
        }

        public T setSizeInBits(int sizeInBits) {
            this.sizeInBits = sizeInBits;
            return self();
        }

        public T setValidRange(FloatValidRange validRange) {
            this.validRange = validRange;
            return self();
        }

        public T setInitialValue(double initialValue) {
            this.initialValue = Double.toString(initialValue);
            return self();
        }

        public T setNumberFormat(NumberFormatType numberFormat) {
            this.numberFormat = numberFormat;
            return self();
        }
    }
}
