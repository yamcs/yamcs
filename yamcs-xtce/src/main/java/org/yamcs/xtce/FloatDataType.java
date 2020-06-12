package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

public abstract class FloatDataType extends NumericDataType {
    private static final long serialVersionUID = 1L;
    Double initialValue;
    /**
     * XTCE: The Valid Range bounds the universe of possible values this Parameter may have. For Telemetry the valid
     * range is always
     * applied before calibration, regardless of the value of validRangeAppliesToCalibrated. For commanding,
     * if validRangeAppliesToCalibrated is false -- it is applied before calibration to the link DataEncoding.
     * 
     */
    FloatValidRange validRange;
    int sizeInBits = 32;

    protected FloatDataType(Builder<?> builder) {
        super(builder);

        this.validRange = builder.validRange;
        if (builder.sizeInBits != null) {
            this.sizeInBits = builder.sizeInBits;
        }

        if (builder.baseType != null && builder.baseType instanceof FloatDataType) {
            FloatDataType baseType = (FloatDataType) builder.baseType;
            if (builder.sizeInBits == null) {
                this.sizeInBits = baseType.sizeInBits;
            }

            if(builder.validRange == null && baseType.validRange != null) {
                this.validRange = baseType.validRange;
            }
        }

        setInitialValue(builder);
    }

    protected FloatDataType(FloatDataType t) {
        super(t);
        this.validRange = t.validRange;
        this.sizeInBits = t.sizeInBits;
    }

    public Double getInitialValue() {
        return initialValue;
    }

    protected void setInitialValue(Object initialValue) {
        if (initialValue instanceof String) {
            this.initialValue = parseString((String) initialValue);
        } else if (initialValue instanceof Number) {
            this.initialValue = ((Number) initialValue).doubleValue();
        } else {
            throw new IllegalArgumentException("Invalid initialValue");
        }
    }

    public int getSizeInBits() {
        return sizeInBits;
    }

    public FloatValidRange getValidRange() {
        return validRange;
    }

    @Override
    public Double parseString(String stringValue) {
        if (sizeInBits == 32) {
            return (double) Float.parseFloat(stringValue);
        } else {
            return Double.parseDouble(stringValue);
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
    }
}
