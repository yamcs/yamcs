package org.yamcs.xtce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class BooleanDataType extends BaseDataType {
    private static final long serialVersionUID = 2L;
    private static final Logger log = LoggerFactory.getLogger(BooleanDataType.class);
    public static final String DEFAULT_ONE_STRING_VALUE = "True";
    public static final String DEFAULT_ZERO_STRING_VALUE = "False";
    Boolean initialValue;

    String oneStringValue = DEFAULT_ONE_STRING_VALUE;
    String zeroStringValue = DEFAULT_ZERO_STRING_VALUE;

    protected BooleanDataType(Builder<?> builder) {
        super(builder);

        if (builder.oneStringValue != null) {
            this.oneStringValue = builder.oneStringValue;
        }
        if (builder.zeroStringValue != null) {
            this.zeroStringValue = builder.zeroStringValue;
        }
        if (builder.baseType instanceof BooleanDataType) {
            BooleanDataType baseType = (BooleanDataType) builder.baseType;
            if (builder.oneStringValue == null && baseType.oneStringValue != null) {
                this.oneStringValue = baseType.oneStringValue;
            }
            if (builder.zeroStringValue == null && baseType.zeroStringValue != null) {
                this.zeroStringValue = baseType.zeroStringValue;
            }
        }

        setInitialValue(builder);
    }

    protected BooleanDataType(BooleanDataType t) {
        super(t);
    }

    @Override
    protected void setInitialValue(Object initialValue) {
        this.initialValue = convertType(initialValue);
    }

    @Override
    public Boolean getInitialValue() {
        return initialValue;
    }

    @Override
    public Boolean convertType(Object value) {
        if (value instanceof String) {
            String stringValue = (String) value;
            if (oneStringValue.equals(stringValue)) {
                return Boolean.TRUE;
            } else if (zeroStringValue.equals(stringValue)) {
                return Boolean.FALSE;
            } else if (oneStringValue.equalsIgnoreCase(stringValue)) {
                log.warn("DEPRECATION: Boolean conversion from string should use '{}' instead of '{}'. "
                        + "This check will be enforced in a future release of Yamcs", oneStringValue, value);
                return Boolean.TRUE;
            } else if (zeroStringValue.equalsIgnoreCase(stringValue)) {
                log.warn("DEPRECATION: Boolean conversion from string should use '{}' instead of '{}'. "
                        + "This check will be enforced in a future release of Yamcs", zeroStringValue, value);
                return Boolean.FALSE;
            } else {
                throw new IllegalArgumentException(
                        "Invalid initialValue, should be '" + oneStringValue + "' or '" + zeroStringValue + "'");
            }
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    @Override
    public String toString(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o ? oneStringValue : zeroStringValue;
        } else {
            throw new IllegalArgumentException("Can only convert a boolean value, not " + o.getClass());
        }
    }

    public String getOneStringValue() {
        return oneStringValue;
    }

    public String getZeroStringValue() {
        return zeroStringValue;
    }

    @Override
    public Type getValueType() {
        return Type.BOOLEAN;
    }

    @Override
    public String getTypeAsString() {
        return "boolean";
    }

    @Override
    public String toString() {
        return "BooleanData encoding: " + encoding;
    }

    public static abstract class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {
        String oneStringValue;
        String zeroStringValue;

        public Builder() {
        }

        public Builder(BooleanDataType dataType) {
            super(dataType);
            this.oneStringValue = dataType.oneStringValue;
            this.zeroStringValue = dataType.zeroStringValue;
        }

        public void setOneStringValue(String oneStringValue) {
            this.oneStringValue = oneStringValue;
        }

        public void setZeroStringValue(String zeroStringValue) {
            this.zeroStringValue = zeroStringValue;
        }
    }
}
